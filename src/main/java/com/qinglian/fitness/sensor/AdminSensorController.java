package com.qinglian.fitness.sensor;

import com.qinglian.fitness.admin.AdminAuthService;
import com.qinglian.fitness.admin.AdminCurrent;
import com.qinglian.fitness.admin.AdminDtos.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminSensorController {
    private final AdminSensorService service;
    private final AdminAuthService auth;
    public AdminSensorController(AdminSensorService service,AdminAuthService auth) { this.service=service; this.auth=auth; }
    @GetMapping("/sensors")
    public PageResult<Map<String,Object>> list(@RequestParam(required=false) String query,@RequestParam(required=false) String state,
        @RequestParam(required=false) String binding,@RequestParam(required=false) Long bedId,@RequestParam(required=false) Long userId,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return service.sensors(query,state,binding,bedId,userId,page,pageSize);
    }
    @GetMapping("/sensors/{id}") public Map<String,Object> detail(@PathVariable long id) { return service.detail(id); }
    @GetMapping("/sensors/{id}/bindings")
    public PageResult<Map<String,Object>> bindings(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return service.bindings(id,page,pageSize);
    }
    @GetMapping("/sensor-workouts")
    public Map<String,Object> workouts(@RequestParam(required=false) String query,@RequestParam(required=false) String status,
        @RequestParam(required=false) Long sensorId,@RequestParam(required=false) Long bedId,@RequestParam(required=false) Long userId,
        @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return service.workouts(query,status,sensorId,bedId,userId,from,to,page,pageSize);
    }
    public record StatusRequest(@NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status) {}
    @GetMapping("/sensor-workouts/{id}") public Map<String,Object> workout(@PathVariable long id) { return service.workout(id); }
    @PutMapping("/sensors/{id}/status") @Transactional
    public Map<String,Object> status(@PathVariable long id,@Valid @RequestBody StatusRequest body,HttpServletRequest request) {
        service.status(id,body.status());
        auth.audit(AdminCurrent.id(request),"UPDATE","SENSOR",id,"更新传感器状态："+body.status(),request.getRemoteAddr());
        return service.detail(id);
    }
    @DeleteMapping("/sensors/{id}/bindings/{bindingId}") @Transactional
    public Map<String,Object> unbind(@PathVariable long id,@PathVariable long bindingId,HttpServletRequest request) {
        service.unbind(id,bindingId);
        auth.audit(AdminCurrent.id(request),"UPDATE","SENSOR",id,"解除传感器绑定，绑定记录："+bindingId,request.getRemoteAddr());
        return service.detail(id);
    }
}
