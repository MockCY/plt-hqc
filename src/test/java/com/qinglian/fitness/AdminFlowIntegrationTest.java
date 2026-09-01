package com.qinglian.fitness;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectsAdminEndpointsAndMaintainsCourseContent() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ADMIN_UNAUTHORIZED"));

        mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"admin","password":"wrong-password"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ADMIN_LOGIN_FAILED"));

        String loginBody = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"admin","password":"admin-test-password"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginBody).path("token").asText();

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courseCount").value(3))
            .andExpect(jsonPath("$.workoutTrend").isArray());

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].nickname").value("测试用户"));

        String categoryBody = mockMvc.perform(post("/api/admin/device-categories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"测试分类","sortOrder":88}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("测试分类"))
            .andExpect(jsonPath("$.deviceCount").value(0))
            .andReturn().getResponse().getContentAsString();
        long categoryId = objectMapper.readTree(categoryBody).path("id").asLong();

        mockMvc.perform(get("/api/admin/device-categories")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == '测试分类')]").exists());

        String deviceBody = mockMvc.perform(post("/api/admin/devices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code":"TEST_REFORMER","name":"测试普拉提床","category":"测试分类",
                      "serialNumber":"ARV-TEST-DEVICE-001","deviceModel":"Arvello Test",
                      "bedType":"测试款","springConfig":"轻弹簧","purchasedOn":"2026-08-31",
                      "connected":false,"active":true,"sortOrder":88
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.category").value("测试分类"))
            .andReturn().getResponse().getContentAsString();
        long deviceId = objectMapper.readTree(deviceBody).path("id").asLong();

        mockMvc.perform(delete("/api/admin/device-categories/{id}", categoryId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DEVICE_CATEGORY_IN_USE"));

        mockMvc.perform(put("/api/admin/device-categories/{id}", categoryId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"测试分类已改","sortOrder":89}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("测试分类已改"))
            .andExpect(jsonPath("$.deviceCount").value(1));

        mockMvc.perform(get("/api/admin/devices?category=测试分类已改")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].serialNumber").value("ARV-TEST-DEVICE-001"));

        mockMvc.perform(post("/api/admin/devices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code":"TEST_UNKNOWN","name":"未知分类设备","category":"未知分类",
                      "serialNumber":"ARV-TEST-DEVICE-002","deviceModel":"Arvello Test",
                      "bedType":"测试款","springConfig":"轻弹簧","purchasedOn":"2026-08-31",
                      "connected":false,"active":true,"sortOrder":89
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DEVICE_CATEGORY_INVALID"));

        mockMvc.perform(delete("/api/admin/devices/{id}", deviceId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/admin/device-categories/{id}", categoryId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        String courseBody = mockMvc.perform(post("/api/admin/courses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"后台测试课程","type":"核心","durationMinutes":18,"level":"初级",
                      "equipment":"无器械","summary":"用于验证后台内容维护流程。","status":"DRAFT",
                      "sortOrder":99,"exerciseIds":[1,2]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("后台测试课程"))
            .andExpect(jsonPath("$.exerciseIds.length()").value(2))
            .andReturn().getResponse().getContentAsString();
        long courseId = objectMapper.readTree(courseBody).path("id").asLong();

        mockMvc.perform(put("/api/admin/courses/{id}", courseId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"后台测试课程","type":"核心","durationMinutes":18,"level":"初级",
                      "equipment":"无器械","summary":"用于验证后台内容维护流程。","status":"PUBLISHED",
                      "sortOrder":99,"exerciseIds":[1,2]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(delete("/api/admin/courses/{id}", courseId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));

        mockMvc.perform(post("/api/admin/auth/logout").header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }
}
