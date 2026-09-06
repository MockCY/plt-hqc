package com.qinglian.fitness.device;

import com.qinglian.fitness.admin.AdminLoginInterceptor;
import com.qinglian.fitness.auth.LoginInterceptor;
import com.qinglian.fitness.auth.SessionService;
import com.qinglian.fitness.common.GlobalExceptionHandler;
import com.qinglian.fitness.config.WebConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.OptionalLong;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeviceAuthenticationTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private SessionService sessions;
    private DeviceRepository devices;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        sessions = context.getBean(SessionService.class);
        devices = context.getBean(DeviceRepository.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void close() { if (context != null) context.close(); }

    @Test
    void missingLoginIsRejectedBeforeTheDeviceQuery() throws Exception {
        mvc.perform(get("/api/devices"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(sessions, devices);
    }

    @Test
    void expiredLoginIsRejectedBeforeTheDeviceQuery() throws Exception {
        when(sessions.verify("expired")).thenReturn(OptionalLong.empty());
        mvc.perform(get("/api/devices").header("Authorization", "Bearer expired"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(devices);
    }

    @Test
    void validLoginPassesTheVerifiedUserToTheDeviceQuery() throws Exception {
        when(sessions.verify("user-two")).thenReturn(OptionalLong.of(2));
        when(devices.list(2)).thenReturn(List.of());
        mvc.perform(get("/api/devices").header("Authorization", "Bearer user-two"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(devices).list(2);
        verifyNoMoreInteractions(devices);
    }

    @Test
    void eachRequestUsesItsOwnAuthenticatedUser() throws Exception {
        when(sessions.verify("user-two")).thenReturn(OptionalLong.of(2));
        when(sessions.verify("user-nine")).thenReturn(OptionalLong.of(9));
        when(devices.list(anyLong())).thenReturn(List.of());
        mvc.perform(get("/api/devices").header("Authorization", "Bearer user-two"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/devices").header("Authorization", "Bearer user-nine"))
            .andExpect(status().isOk());
        verify(devices).list(2);
        verify(devices).list(9);
        verifyNoMoreInteractions(devices);
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean SessionService sessions() { return mock(SessionService.class); }
        @Bean DeviceRepository devices() { return mock(DeviceRepository.class); }
        @Bean LoginInterceptor login(SessionService sessions) { return new LoginInterceptor(sessions, new ObjectMapper()); }
        @Bean WebConfig webConfig(LoginInterceptor login) { return new WebConfig(login, mock(AdminLoginInterceptor.class), "target/test-media"); }
        @Bean DeviceController controller(DeviceRepository devices) { return new DeviceController(devices); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
    }
}
