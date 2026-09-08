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
    public SensorController(SensorService service) { this.service = service; }
    @GetMapping("/api/v1/sensor/health")
    public Map<String,Object> health() { return Map.of("ok",true,"service","arvello-server","time",Instant.now()); }
    @PostMapping("/api/v1/sensor/readings") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,Object> receive(@RequestHeader(value="X-Device-Id",required=false) String id,
        @Valid @RequestBody SensorDtos.Reading body) {
        return service.receive(id,body);
    }
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
}
