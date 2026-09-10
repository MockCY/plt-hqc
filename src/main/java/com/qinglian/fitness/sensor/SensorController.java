package com.qinglian.fitness.sensor;

import com.qinglian.fitness.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestController
public class SensorController {
    private final SensorService service;
    private final SensorV4Service v4;
    private final tools.jackson.databind.ObjectMapper json;
    private final jakarta.validation.Validator validator;
    public SensorController(SensorService service,SensorV4Service v4,tools.jackson.databind.ObjectMapper json,jakarta.validation.Validator validator) {
        this.service=service; this.v4=v4; this.json=json; this.validator=validator;
    }
    @GetMapping("/api/v1/sensor/health")
    public Map<String,Object> health() { return Map.of("ok",true,"service","arvello-server","time",Instant.now()); }
    @PostMapping("/api/v1/sensor/readings") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,Object> receive(@RequestHeader(value="X-Device-Id",required=false) String id,
        @RequestBody tools.jackson.databind.JsonNode body) {
        if (!body.isObject() || !body.has("schemaVersion") || !body.get("schemaVersion").isIntegralNumber())
            throw SensorService.error(HttpStatus.BAD_REQUEST,"INVALID_PAYLOAD","必须提供协议版本");
        int version=body.get("schemaVersion").asInt();
        String type=body.path("recordType").asText("");
        if (version==3 && (type.isEmpty() || type.equals("telemetry")))
            return service.receive(id,payload(body,SensorDtos.Reading.class));
        if (version==4 && type.equals("telemetry")) return v4.telemetry(id,payload(body,SensorV4Dtos.Telemetry.class));
        if (version==4 && type.equals("training_summary")) return v4.summary(id,payload(body,SensorV4Dtos.Summary.class));
        throw SensorService.error(HttpStatus.BAD_REQUEST,"UNSUPPORTED_RECORD","不支持的协议版本或记录类型");
    }
    private <T> T payload(tools.jackson.databind.JsonNode body,Class<T> type) {
        T value;
        try { value=json.treeToValue(body,type); }
        catch (RuntimeException e) { throw SensorService.error(HttpStatus.BAD_REQUEST,"INVALID_PAYLOAD","传感器字段类型不正确"); }
        var errors=validator.validate(value);
        if (!errors.isEmpty()) {
            var first=errors.iterator().next();
            throw SensorService.error(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",first.getPropertyPath()+"："+first.getMessage());
        }
        return value;
    }
    @PostMapping("/api/v1/device/register")
    public Map<String,Object> deviceRegister(@RequestHeader(value="X-Device-Id",required=false) String id,
        @Valid @RequestBody SensorV4Dtos.Registration body) { return v4.register(id,body); }
    @PostMapping("/api/v1/sensor/claim-confirm")
    public Map<String,Object> confirm(@RequestHeader(value="X-Device-Id",required=false) String id,
        @Valid @RequestBody SensorDtos.Confirm body) {
        service.confirm(id,body.challengeId()); return Map.of("ok",true);
    }
    @PostMapping("/api/admin/sensors") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> register(@Valid @RequestBody SensorDtos.Register body) { return service.register(body.deviceId()); }
    @PostMapping("/api/v1/device-bed-bindings/challenges")
    public Map<String,Object> challenge(HttpServletRequest request,@Valid @RequestBody SensorDtos.Claim body) { return service.challenge(CurrentUser.id(request),body); }
    @PostMapping("/api/v1/device-bed-bindings")
    public Map<String,Object> bind(HttpServletRequest request,@Valid @RequestBody SensorDtos.Binding body) { return service.bind(CurrentUser.id(request),body.challengeId()); }
    @DeleteMapping("/api/v1/device-bed-bindings/{id}")
    public Map<String,Object> unbind(HttpServletRequest request,@PathVariable long id) { service.unbind(CurrentUser.id(request),id); return Map.of("ok",true); }
    @GetMapping("/api/v1/beds/{bedSn}/sensor/latest")
    public Map<String,Object> latest(HttpServletRequest request,@PathVariable String bedSn) { return service.latest(CurrentUser.id(request),bedSn); }
    @GetMapping("/api/v1/beds/{bedSn}/sensor/sessions")
    public Map<String,Object> sessions(HttpServletRequest request,@PathVariable String bedSn,@RequestParam(required=false) Long before) { return service.history(CurrentUser.id(request),bedSn,before); }
    @GetMapping("/api/v1/sensor/sessions")
    public Map<String,Object> userSessions(HttpServletRequest request,@RequestParam(required=false) Long before) { return service.userHistory(CurrentUser.id(request),before); }
    @GetMapping("/api/v1/sensor/stats")
    public Map<String,Object> userStats(HttpServletRequest request) { return service.trainingStats(CurrentUser.id(request)); }
}
