package com.qinglian.fitness.admin;

import com.qinglian.fitness.admin.AdminDtos.ExerciseRow;
import com.qinglian.fitness.mapper.AdminMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 在专用本机 MySQL 测试库执行真实 Mapper，验证方言、转义与超过 100 条动作的分页搜索。 */
@EnabledIfSystemProperty(named = "exercise.search.mysql.url",
    matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/exercise_search_test(?:\\?.*)?")
class ExerciseSearchMapperMysqlTest {

    private static SqlSessionFactory factory;

    @BeforeAll
    static void prepareIsolatedDatabase() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
            System.getProperty("exercise.search.mysql.url"), "root", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            // 仅允许显式指定的本机专用测试库，不能加载应用配置或触及业务库。
            statement.execute("drop table if exists exercises");
            statement.execute("""
                create table exercises (
                    id bigint primary key, name varchar(80) not null, body_part varchar(30) not null,
                    level varchar(30) not null, equipment varchar(80) not null, suggested_sets int not null,
                    target varchar(40), cue varchar(500), safety_tip varchar(500), cover_image varchar(500),
                    video_url varchar(500), video_cover_image varchar(500), video_duration_seconds int,
                    background_music_url varchar(500), focus_image_url varchar(500), focus_parts varchar(100),
                    spring_sets json, spring_counts json, key_points text, common_mistakes text,
                    instruction_audio_url varchar(500), status varchar(20) not null, sort_order int not null,
                    created_at datetime(3) not null, updated_at datetime(3) not null
                ) character set utf8mb4 collate utf8mb4_0900_as_cs
                """);
            try (PreparedStatement insert = connection.prepareStatement("""
                insert into exercises (id, name, body_part, level, equipment, suggested_sets, status,
                                       sort_order, created_at, updated_at)
                values (?, ?, ?, ?, '核心床', 2, ?, 0, '2026-10-24 10:00:00', '2026-10-24 10:00:00')
                """)) {
                insert(insert, 1, "跪姿W臂后展", "肩背", "基础", "PUBLISHED");
                insert(insert, 2, "跪姿W手", "肩背体态", "基础", "PUBLISHED");
                insert(insert, 3, "单腿跪姿W手展背", "肩背体态", "进阶", "PUBLISHED");
                for (int id = 4; id <= 208; id++) insert(insert, id, "分页动作" + id, "核心训练", "基础", "PUBLISHED");
                insert(insert, 209, "草稿动作", "肩背体态", "基础", "DRAFT");
                insert(insert, 210, "下架动作", "肩背体态", "基础", "ARCHIVED");
                insert(insert, 211, "字面%动作", "全身", "挑战", "PUBLISHED");
                insert(insert, 212, "字面_动作", "下肢", "挑战", "PUBLISHED");
                insert(insert, 213, "字面!动作", "拉伸协调", "挑战", "PUBLISHED");
            }
        }
        Configuration configuration = new Configuration(new Environment("exercise-search-test",
            new JdbcTransactionFactory(), dataSource));
        try (InputStream xml = Resources.getResourceAsStream("mappers/AdminMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mappers/AdminMapper.xml", configuration.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void lowercaseSearchFindsOldActionOutsideTheFirstHundredRows() {
        try (SqlSession session = factory.openSession()) {
            ExerciseSearchService service = new ExerciseSearchService(session.getMapper(AdminMapper.class));
            var page = service.search("  跪姿w  ", null, "1", "20", null);
            assertEquals(3, page.total());
            assertEquals(List.of(3L, 2L, 1L), page.items().stream().map(ExerciseRow::id).toList());
        }
    }

    @Test
    void queryMatchesTypeAndDifficulty() {
        try (SqlSession session = factory.openSession()) {
            ExerciseSearchService service = new ExerciseSearchService(session.getMapper(AdminMapper.class));
            assertEquals(3, service.search("肩背", null, null, null, null).total());
            assertEquals(3, service.search("肩背体态", null, null, null, null).total());
            assertEquals(3, service.search("体态", null, null, null, null).total());
            assertEquals(2, service.search("拉伸协调", null, null, null, null).total());
            assertEquals(1, service.search("臀腿塑形", null, null, null, null).total());
            assertEquals(1, service.search("进阶", null, null, null, null).total());
            assertEquals(0, service.search("没有该动作", null, null, null, null).total());
        }
    }

    @Test
    void literalWildcardsCannotExpandTheSearchOrInjectSql() {
        try (SqlSession session = factory.openSession()) {
            ExerciseSearchService service = new ExerciseSearchService(session.getMapper(AdminMapper.class));
            assertEquals(List.of(211L), service.search("%", null, null, null, null).items().stream().map(ExerciseRow::id).toList());
            assertEquals(List.of(212L), service.search("_", null, null, null, null).items().stream().map(ExerciseRow::id).toList());
            assertEquals(List.of(213L), service.search("!", null, null, null, null).items().stream().map(ExerciseRow::id).toList());
            assertEquals(0, service.search("' OR 1=1 --", null, null, null, null).total());
        }
    }

    @Test
    void selectedIdsCanIncludeDraftAndArchivedActionsWithoutLeakingOtherRows() {
        try (SqlSession session = factory.openSession()) {
            ExerciseSearchService service = new ExerciseSearchService(session.getMapper(AdminMapper.class));
            assertEquals(List.of(210L, 209L, 1L), service.search(null, "ALL", "1", "100", "1,209,210").items()
                .stream().map(ExerciseRow::id).toList());
            assertEquals(List.of(1L), service.search(null, null, null, null, "1,209,210").items()
                .stream().map(ExerciseRow::id).toList());
            assertEquals(1, service.search(null, "DRAFT", null, null, null).total());
            assertEquals(1, service.search(null, "ARCHIVED", null, null, null).total());
            assertEquals(0, service.search("分页", "ALL", null, null, "1,209,210").total());
            assertEquals(0, session.getMapper(AdminMapper.class).countExerciseSearch(null, null, List.of(), List.of()));
            assertTrue(session.getMapper(AdminMapper.class).searchExercises(null, null, List.of(), List.of(), 20, 0).isEmpty());
        }
    }

    @Test
    void tiedSortValuesHaveStablePagesWithoutDuplicatesOrMissingRows() {
        try (SqlSession session = factory.openSession()) {
            ExerciseSearchService service = new ExerciseSearchService(session.getMapper(AdminMapper.class));
            List<Long> ids = new ArrayList<>();
            for (int page = 1; page <= 3; page++) {
                var result = service.search(null, null, String.valueOf(page), "100", null);
                assertEquals(211, result.total());
                ids.addAll(result.items().stream().map(ExerciseRow::id).toList());
            }
            assertEquals(211, ids.size());
            assertEquals(211, new HashSet<>(ids).size());
            assertEquals(213L, ids.getFirst());
            assertEquals(1L, ids.getLast());
            assertTrue(service.search(null, null, "4", "100", null).items().isEmpty());
        }
    }

    private static void insert(PreparedStatement statement, long id, String name, String bodyPart, String level, String status)
        throws Exception {
        statement.setLong(1, id);
        statement.setString(2, name);
        statement.setString(3, bodyPart);
        statement.setString(4, level);
        statement.setString(5, status);
        statement.executeUpdate();
    }
}
