package com.qinglian.fitness.catalog;

import com.qinglian.fitness.admin.AdminController;
import com.qinglian.fitness.admin.AdminDtos.ExerciseRequest;
import com.qinglian.fitness.admin.AdminRepository;
import com.qinglian.fitness.common.GlobalExceptionHandler;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpringCountsTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static String request(String springFields) {
        return """
            {"name":"测试动作","bodyPart":"核心训练","level":"基础","equipment":"核心床（Reformer）",
             "suggestedSets":2,"cue":"动作要领","safetyTip":"安全提示","status":"DRAFT","sortOrder":0,%s}
            """.formatted(springFields);
    }

    @Test
    void acceptsAllColorsIncludingZeroAndUpperBoundAndClearsLegacyCounts() {
        var value = JSON.readValue(request("\"springSets\":[2,3],\"springCounts\":{\"red\":20,\"green\":1,\"yellow\":2,\"blue\":0}"), ExerciseRequest.class);
        assertThat(value.springCounts()).isEqualTo(new SpringCounts(20, 1, 2, 0));
        assertThat(value.springSets()).isEmpty();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(value)).isEmpty();
        }
        var encoded = JSON.readTree(JSON.writeValueAsString(value));
        assertThat(encoded.path("springCounts").path("blue").intValue()).isZero();
        assertThat(encoded.path("springCounts").path("red").intValue()).isEqualTo(20);
    }

    @Test
    void preservesLegacyCountsWithoutInventingColors() {
        var value = JSON.readValue(request("\"springSets\":[2,3]"), ExerciseRequest.class);
        assertThat(value.springSets()).isEqualTo(List.of(2, 3));
        assertThat(value.springCounts()).isNull();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(value)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"red\":-1,\"green\":0,\"yellow\":0,\"blue\":0}",
        "{\"red\":0,\"green\":21,\"yellow\":0,\"blue\":0}",
        "{\"red\":0,\"green\":0,\"yellow\":-1,\"blue\":0}",
        "{\"red\":0,\"green\":0,\"yellow\":0,\"blue\":21}",
        "{\"red\":null,\"green\":0,\"yellow\":0,\"blue\":0}",
        "{\"red\":1,\"green\":0,\"yellow\":0}",
        "{\"red\":1.5,\"green\":0,\"yellow\":0,\"blue\":0}",
        "{\"red\":1,\"green\":\"2\",\"yellow\":0,\"blue\":0}",
        "[]"
    })
    void rejectsInvalidCountsOnCreateAndUpdateBeforeWriting(String counts) throws Exception {
        var repository = mock(AdminRepository.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminController(null, repository, null, null, null))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = request("\"springCounts\":" + counts);
        mvc.perform(post("/api/admin/exercises").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/exercises/1").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
}
