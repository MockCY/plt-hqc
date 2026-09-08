package com.qinglian.fitness.sensor;

import com.qinglian.fitness.admin.AdminLoginInterceptor;
import com.qinglian.fitness.auth.LoginInterceptor;
import com.qinglian.fitness.auth.SessionService;
import com.qinglian.fitness.common.GlobalExceptionHandler;
import com.qinglian.fitness.config.WebConfig;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SensorAuthenticationTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    SensorService sensors;
    SessionService sessions;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).build();
        sensors=context.getBean(SensorService.class); sessions=context.getBean(SessionService.class);
    }
    @AfterEach void close() { context.close(); }
    @Test void deviceUploadNeedsNoUserLoginOrDeviceKey() throws Exception {
        when(sensors.receive(eq("ARVELLO-80A5419205D4"),any())).thenReturn(Map.of("ok",true));
        mvc.perform(post("/api/v1/sensor/readings").header("X-Device-Id","ARVELLO-80A5419205D4")
            .contentType("application/json").content("""
            {"schemaVersion":3,"deviceId":"ARVELLO-80A5419205D4","bootId":"boot0001","sequence":1,
             "uptimeMs":1000,"sensorOk":false,"moving":null,"standby":false,"countType":"continuous_cycle","repetitionCount":0,"futureField":1}
            """))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.ok").value(true));
        verify(sensors).receive(eq("ARVELLO-80A5419205D4"),any());
        verifyNoInteractions(sessions);
    }
    @Test void bedQueryStillRequiresUserLogin() throws Exception {
        mvc.perform(get("/api/v1/beds/BED-1/sensor/latest")).andExpect(status().isUnauthorized());
        verifyNoInteractions(sensors);
    }
    @Test void healthDoesNotNeedEitherCredential() throws Exception {
        mvc.perform(get("/api/v1/sensor/health")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        verifyNoInteractions(sensors,sessions);
    }
    @Test void missingBootIdFailsValidation() throws Exception {
        mvc.perform(post("/api/v1/sensor/readings").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(sensors);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean SensorService sensors() { return mock(SensorService.class); }
        @Bean SessionService sessions() { return mock(SessionService.class); }
        @Bean LoginInterceptor login(SessionService s) { return new LoginInterceptor(s,new ObjectMapper()); }
        @Bean WebConfig web(LoginInterceptor login) { return new WebConfig(login,mock(AdminLoginInterceptor.class),"target/test-media"); }
        @Bean SensorController controller(SensorService s) { return new SensorController(s); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean SensorErrors sensorErrors() { return new SensorErrors(); }
    }
}
