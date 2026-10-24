package com.qinglian.fitness.admin;

import com.qinglian.fitness.admin.AdminDtos.ExerciseRow;
import com.qinglian.fitness.admin.AdminDtos.PageResult;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.catalog.ExerciseCategory;
import com.qinglian.fitness.mapper.AdminMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 在数据库中分页搜索后台动作库，并为编辑器批量回显已选动作。 */
@Service
public class ExerciseSearchService {

    private static final int MAX_QUERY_LENGTH = 80;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_IDS = 100;
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED", "ALL");

    private final AdminMapper mapper;

    public ExerciseSearchService(AdminMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按名称、类型或难度进行包含查询，英文字母不区分大小写。
     *
     * @param query 可省略的搜索词，去除首尾空白后最多 80 个字符；%、_、! 按字面匹配
     * @param status 默认 PUBLISHED；ALL 表示不限制状态
     * @param page 从 1 开始的页码，必须为正整数
     * @param pageSize 每页 1 至 100 条，默认 20 条
     * @param ids 可省略的逗号分隔正整数 ID，最多 100 个；显式空字符串只返回空结果
     * @return 保留后台既有分页与动作响应字段，找不到匹配项时返回空列表
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public PageResult<ExerciseRow> search(String query, String status, String page, String pageSize, String ids) {
        String pattern = searchPattern(query);
        String normalizedStatus = normalizeStatus(status);
        int parsedPage = positiveInteger(page, 1, Integer.MAX_VALUE, "page");
        int parsedPageSize = positiveInteger(pageSize, 20, MAX_PAGE_SIZE, "pageSize");
        List<Long> selectedIds = parseIds(ids);
        if (selectedIds != null && selectedIds.isEmpty()) {
            return new PageResult<>(List.of(), 0, parsedPage, parsedPageSize);
        }

        // 总数与当前页在同一短只读事务内读取，避免并发更新造成两次查询使用不同快照。
        List<String> categoryAliases = ExerciseCategory.searchValues(query);
        long total = mapper.countExerciseSearch(pattern, normalizedStatus, selectedIds, categoryAliases);
        long offset = (long) (parsedPage - 1) * parsedPageSize;
        List<ExerciseRow> items = offset >= total ? List.of()
            : mapper.searchExercises(pattern, normalizedStatus, selectedIds, categoryAliases, parsedPageSize, offset);
        return new PageResult<>(items, total, parsedPage, parsedPageSize);
    }

    private String searchPattern(String query) {
        if (query == null || query.isBlank()) return null;
        String normalized = query.trim();
        if (normalized.codePointCount(0, normalized.length()) > MAX_QUERY_LENGTH) {
            throw invalid("搜索词不能超过 80 个字符");
        }
        // 先转义转义符自身，阻止搜索词被解释为 SQL LIKE 通配符。
        return "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank() ? "PUBLISHED" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw invalid("status 只能为 ALL、DRAFT、PUBLISHED 或 ARCHIVED");
        return "ALL".equals(normalized) ? null : normalized;
    }

    private int positiveInteger(String value, int defaultValue, int maximum, String name) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= 1 && parsed <= maximum) return parsed;
        } catch (NumberFormatException ignored) {
            // 数字格式与越界统一转换为业务校验错误，不暴露底层解析异常。
        }
        throw invalid(name + " 必须为 1 至 " + maximum + " 的整数");
    }

    private List<Long> parseIds(String ids) {
        if (ids == null) return null;
        if (ids.isBlank()) return List.of();
        String[] values = ids.split(",", MAX_IDS + 1);
        if (values.length > MAX_IDS) throw invalid("ids 最多允许 100 个动作 ID");
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (String value : values) {
            try {
                long id = Long.parseLong(value.trim());
                if (id <= 0) throw invalid("ids 必须为逗号分隔的正整数");
                uniqueIds.add(id);
            } catch (NumberFormatException ignored) {
                throw invalid("ids 必须为逗号分隔的正整数");
            }
        }
        return new ArrayList<>(uniqueIds);
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
