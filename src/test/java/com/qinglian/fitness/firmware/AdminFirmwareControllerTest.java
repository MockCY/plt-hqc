package com.qinglian.fitness.firmware;

import com.qinglian.fitness.admin.AdminAuthService;
import com.qinglian.fitness.admin.AdminLoginInterceptor;
import com.qinglian.fitness.auth.LoginInterceptor;
import com.qinglian.fitness.auth.SessionService;
import com.qinglian.fitness.common.GlobalExceptionHandler;
import com.qinglian.fitness.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminFirmwareControllerTest {
    @TempDir Path root;

    @Configuration
    @EnableWebMvc
    static class TestWebConfig {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
        @Bean FirmwareService firmwareService(@Value("${test.firmware.root}") String root, ObjectMapper mapper) { return new FirmwareService(root, mapper); }
        @Bean AdminFirmwareService adminFirmwareService(FirmwareService firmware, ObjectMapper mapper) { return new AdminFirmwareService(firmware, mapper); }
        @Bean FirmwareController firmwareController(FirmwareService firmware, ObjectMapper mapper) { return new FirmwareController(firmware, mapper); }
        @Bean AdminFirmwareController adminFirmwareController(AdminFirmwareService firmware) { return new AdminFirmwareController(firmware); }
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean AdminAuthService adminAuthService() { return mock(AdminAuthService.class); }
        @Bean SessionService sessionService() { return mock(SessionService.class); }
        @Bean LoginInterceptor loginInterceptor(SessionService service, ObjectMapper mapper) { return new LoginInterceptor(service, mapper); }
        @Bean AdminLoginInterceptor adminLoginInterceptor(AdminAuthService service, ObjectMapper mapper) { return new AdminLoginInterceptor(service, mapper); }
        // Exercise the production interceptor paths, rather than adding an ad-hoc test interceptor.
        @Bean WebConfig webConfig(LoginInterceptor user, AdminLoginInterceptor admin, @Value("${test.firmware.root}") String root) { return new WebConfig(user, admin, root); }
    }

    private AnnotationConfigWebApplicationContext context() {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("test.firmware.root", root.toString())));
        context.register(TestWebConfig.class);
        context.refresh();
        when(context.getBean(AdminAuthService.class).verify("valid-admin")).thenReturn(Optional.of(new AdminAuthService.VerifiedAdmin(1, "admin")));
        return context;
    }
    private static MockMultipartFile image() {
        return new MockMultipartFile("file", "application.ino.bin", "application/octet-stream", AdminFirmwareServiceTest.validImage("AVS-80A5", "4.1.2"));
    }

    @Test void allManagementRoutesRequireAdminLoginAndDraftIsNotPublic() throws Exception {
        try (var context = context()) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/api/admin/firmware")).andExpect(status().isUnauthorized());
            mvc.perform(multipart("/api/admin/firmware").file(image()).param("model", "AVS-80A5").param("version", "4.1.2"))
                    .andExpect(status().isUnauthorized());
            for (String action : new String[]{"publish", "withdraw"}) {
                mvc.perform(post("/api/admin/firmware/AVS-80A5-4_1_2/" + action)).andExpect(status().isUnauthorized());
            }
            mvc.perform(get("/api/admin/firmware/AVS-80A5-4_1_2/firmware.bin")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/admin/firmware").header("Authorization", "Bearer ordinary-user")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/firmware/latest").param("model", "AVS-80A5").param("currentVersion", "4.1.1"))
                    .andExpect(status().isOk());
            var service = context.getBean(AdminFirmwareService.class);
            service.upload(image(), "AVS-80A5", "4.1.2", "");
            mvc.perform(get("/api/v1/firmware/releases/AVS-80A5-4_1_2")).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/firmware/releases/AVS-80A5-4_1_2/firmware.bin")).andExpect(status().isNotFound());
            // Public paths never expose upload, publication or withdrawal endpoints.
            var unmapped = mvc.perform(post("/api/v1/firmware/AVS-80A5-4_1_2/publish")).andReturn();
            assertInstanceOf(org.springframework.web.servlet.NoHandlerFoundException.class, unmapped.getResolvedException());
            assertTrue(unmapped.getResponse().getStatus() >= 400);
            assertEquals("DRAFT", service.list().getFirst().status());
        }
    }

    @Test void authenticatedUploadListPublishDownloadAndWithdrawalUseTheAgreedContract() throws Exception {
        try (var context = context()) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var json = context.getBean(ObjectMapper.class);
            var response = mvc.perform(multipart("/api/admin/firmware").file(image()).param("model", "AVS-80A5").param("version", "4.1.2")
                            .param("notes", "新版本").header("Authorization", "Bearer valid-admin"))
                    .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
            var uploaded = json.readTree(response.getContentAsByteArray());
            assertEquals("DRAFT", uploaded.get("status").asText());
            assertEquals("AVS-80A5-4_1_2", uploaded.get("releaseId").asText());
            assertNotNull(java.time.Instant.parse(uploaded.get("createdAt").asText()));
            var listed = mvc.perform(get("/api/admin/firmware").header("Authorization", "Bearer valid-admin"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            assertEquals(1, json.readTree(listed.getContentAsByteArray()).get("releases").size());
            var download = mvc.perform(get("/api/admin/firmware/AVS-80A5-4_1_2/firmware.bin").header("Authorization", "Bearer valid-admin"))
                    .andExpect(status().isOk()).andExpect(header().string("Content-Length", "592")).andReturn().getResponse();
            assertArrayEquals(image().getBytes(), download.getContentAsByteArray());
            var published = mvc.perform(post("/api/admin/firmware/AVS-80A5-4_1_2/publish").header("Authorization", "Bearer valid-admin"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            assertEquals("CURRENT", json.readTree(published.getContentAsByteArray()).get("status").asText());
            var withdrawn = mvc.perform(post("/api/admin/firmware/AVS-80A5-4_1_2/withdraw").header("Authorization", "Bearer valid-admin"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            assertEquals("HISTORY", json.readTree(withdrawn.getContentAsByteArray()).get("status").asText());
            var latest = mvc.perform(get("/api/v1/firmware/latest").param("model", "AVS-80A5").param("currentVersion", "4.1.1"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            assertFalse(json.readTree(latest.getContentAsByteArray()).get("published").asBoolean());
        }
    }

    @Test void missingMultipartFieldsReturnUsefulBadRequestErrors() throws Exception {
        try (var context = context()) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var json = context.getBean(ObjectMapper.class);
            var noFile = mvc.perform(multipart("/api/admin/firmware").param("model", "AVS-80A5").param("version", "4.1.2")
                            .header("Authorization", "Bearer valid-admin"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("FIRMWARE_FILE_REQUIRED", json.readTree(noFile.getContentAsByteArray()).get("code").asText());
            mvc.perform(multipart("/api/admin/firmware").file(image()).param("version", "4.1.2").header("Authorization", "Bearer valid-admin"))
                    .andExpect(status().isBadRequest());
            mvc.perform(multipart("/api/admin/firmware").file(image()).param("model", "AVS-80A5").header("Authorization", "Bearer valid-admin"))
                    .andExpect(status().isBadRequest());
            assertTrue(context.getBean(AdminFirmwareService.class).list().isEmpty());
        }
    }
}
