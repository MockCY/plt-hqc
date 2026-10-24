package com.qinglian.fitness.admin;

import com.qinglian.fitness.common.GlobalExceptionHandler;
import com.qinglian.fitness.mapper.AdminMapper;
import com.qinglian.fitness.media.MediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证搜索接口鉴权、参数错误、空 ID 语义与大页码，不依赖业务数据库。 */
class ExerciseSearchServiceTest {

    private AdminMapper mapper;
    private ExerciseSearchService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mapper = mock(AdminMapper.class);
        service = new ExerciseSearchService(mapper);
        AdminAuthService auth = mock(AdminAuthService.class);
        when(auth.verify("exercise-search-test-token"))
            .thenReturn(Optional.of(new AdminAuthService.VerifiedAdmin(1, "test-admin")));
        AdminController controller = new AdminController(auth, mock(AdminRepository.class), mock(MediaStorageService.class),
            mock(DeviceQrCodeService.class), mock(DeviceExcelExportService.class), service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .addInterceptors(new AdminLoginInterceptor(auth, new ObjectMapper()))
            .build();
    }

    @Test
    void searchRouteUsesPublishedDefaultsAndExistingPageContract() throws Exception {
        mvc.perform(get("/api/admin/exercises/search").header("Authorization", "Bearer exercise-search-test-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(20));
        verify(mapper).countExerciseSearch(null, "PUBLISHED", null, List.of());
    }

    @Test
    void searchRequiresAdminSession() throws Exception {
        mvc.perform(get("/api/admin/exercises/search")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/exercises/search").header("Authorization", "Bearer invalid-test-token"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(mapper);
    }

    @Test
    void explicitEmptyIdsReturnEmptyWithoutQueryingAllExercises() throws Exception {
        mvc.perform(get("/api/admin/exercises/search").param("ids", "").param("status", "ALL")
                .header("Authorization", "Bearer exercise-search-test-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        verifyNoInteractions(mapper);
    }

    @Test
    void trimsEscapesAndBindsQueryAndDeduplicatesSelectedIds() {
        service.search("  W%_!  ", "all", "1", "100", "7, 7,9");
        verify(mapper).countExerciseSearch("%W!%!_!!%", null, List.of(7L, 9L), List.of());
    }

    @Test
    void largePageNeverWrapsOffsetAndDoesNotLoadEarlierRows() {
        when(mapper.countExerciseSearch(null, "PUBLISHED", null, List.of())).thenReturn(Long.MAX_VALUE);
        when(mapper.searchExercises(isNull(), eq("PUBLISHED"), isNull(), any(), anyInt(), anyLong())).thenReturn(List.of());
        service.search(null, null, String.valueOf(Integer.MAX_VALUE), "100", null);
        verify(mapper).searchExercises(null, "PUBLISHED", null, List.of(), 100, 214748364600L);
    }

    @Test
    void mapperFailuresRemainFailuresInsteadOfEmptySearchResults() {
        when(mapper.countExerciseSearch(any(), any(), any(), any())).thenThrow(new IllegalStateException("test database failure"));
        assertThrows(IllegalStateException.class, () -> service.search("test", null, null, null, null));
    }

    @ParameterizedTest
    @MethodSource("invalidParameters")
    void invalidSearchInputsReturnExistingValidationError(String name, String value) throws Exception {
        mvc.perform(get("/api/admin/exercises/search").param(name, value)
                .header("Authorization", "Bearer exercise-search-test-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(mapper);
    }

    static Stream<String[]> invalidParameters() {
        return Stream.of(
            new String[]{"query", "动".repeat(81)}, new String[]{"status", "INVALID"},
            new String[]{"page", "0"}, new String[]{"page", "-1"}, new String[]{"page", "1.5"},
            new String[]{"page", "2147483648"}, new String[]{"pageSize", "0"}, new String[]{"pageSize", "101"},
            new String[]{"pageSize", "abc"}, new String[]{"ids", "0"}, new String[]{"ids", "-1"},
            new String[]{"ids", "1,,2"}, new String[]{"ids", "1,"}, new String[]{"ids", "1,abc"},
            new String[]{"ids", "9223372036854775808"}, new String[]{"ids", "1,".repeat(100) + "1"}
        );
    }
}
