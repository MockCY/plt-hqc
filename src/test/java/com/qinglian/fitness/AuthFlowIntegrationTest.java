package com.qinglian.fitness;

import com.qinglian.fitness.auth.WechatGateway;
import com.qinglian.fitness.auth.WechatIdentity;
import com.qinglian.fitness.auth.WechatPhone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthFlowIntegrationTest.FakeWechatConfiguration.class)
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authenticatesWechatUserAndProtectsBusinessEndpoints() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/courses/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("全身激活"))
            .andExpect(jsonPath("$.exercises", hasSize(3)));

        mockMvc.perform(get("/api/plans/catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)));

        String body = mockMvc.perform(post("/api/auth/wechat-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"loginCode":"valid-login-code","phoneCode":"valid-phone-code"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.phoneBound").value(true))
            .andExpect(jsonPath("$.user.phone").value("138****5678"))
            .andReturn().getResponse().getContentAsString();

        JsonNode login = objectMapper.readTree(body);
        String token = login.path("token").asText();

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.phoneBound").value(true));

        mockMvc.perform(get("/api/courses").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].title").value("全身激活"));

        mockMvc.perform(get("/api/devices/current")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DEVICE_NOT_BOUND"));

        mockMvc.perform(post("/api/devices/bind")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"serialNumber":"ARV240428MNT001"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Arvello 柔力核心床"))
            .andExpect(jsonPath("$.category").value("核心床"))
            .andExpect(jsonPath("$.serialNumber").value("ARV240428MNT001"))
            .andExpect(jsonPath("$.bedType").value("标准款 · Mint"))
            .andExpect(jsonPath("$.boundAt").exists());

        mockMvc.perform(get("/api/devices/current")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serialNumber").value("ARV240428MNT001"));

        mockMvc.perform(delete("/api/devices/current")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/devices/current")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DEVICE_NOT_BOUND"));

        mockMvc.perform(put("/api/favorites/EXERCISE/1")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorite").value(true));

        mockMvc.perform(get("/api/favorites?type=EXERCISE")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].itemId").value(1));

        mockMvc.perform(post("/api/custom-courses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"我的晚间课程","durationMinutes":20,"summary":"肩背与核心","exerciseIds":[1,2]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("我的晚间课程"))
            .andExpect(jsonPath("$.exerciseIds", hasSize(2)));

        mockMvc.perform(put("/api/plans/2/select")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.selected").value(true));

        mockMvc.perform(get("/api/plans/current")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("核心稳定计划"));

        mockMvc.perform(put("/api/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nickname":"ARVELLO 测试用户"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("ARVELLO 测试用户"));

        mockMvc.perform(put("/api/me/settings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reminderEnabled":false,"soundEnabled":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reminderEnabled").value(false));

        mockMvc.perform(post("/api/feedback")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"category":"功能建议","content":"希望增加新的课程"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(post("/api/campaigns/august-abs/checkins")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkedInToday").value(true))
            .andExpect(jsonPath("$.totalCheckins").value(1));

        mockMvc.perform(post("/api/campaigns/august-abs/checkins")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCheckins").value(1));

        mockMvc.perform(post("/api/workout-records")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"courseId":1,"durationMinutes":12,"completionPercent":100}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.courseTitle").value("全身激活"));

        mockMvc.perform(post("/api/workout-records")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customCourseId":1,"durationMinutes":20,"completionPercent":100}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.courseTitle").value("我的晚间课程"));

        mockMvc.perform(get("/api/workout-records")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/workout-records/stats")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completedCount").value(2))
            .andExpect(jsonPath("$.totalMinutes").value(32));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());

        String secondLoginBody = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"valid-login-code"}
                    """))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String secondToken = objectMapper.readTree(secondLoginBody).path("token").asText();

        mockMvc.perform(delete("/api/me").header("Authorization", "Bearer " + secondToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + secondToken))
            .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class FakeWechatConfiguration {

        @Bean
        @Primary
        WechatGateway fakeWechatGateway() {
            return new WechatGateway() {
                @Override
                public WechatIdentity exchangeLoginCode(String loginCode) {
                    return new WechatIdentity("test-openid", "test-unionid");
                }

                @Override
                public WechatPhone exchangePhoneCode(String phoneCode) {
                    return new WechatPhone("+8613812345678", "13812345678", "86");
                }
            };
        }
    }
}
