package com.qinglian.fitness.device;

import com.qinglian.fitness.auth.CurrentUser;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.device.DeviceDtos.BindRequest;
import com.qinglian.fitness.device.DeviceDtos.BindResult;
import com.qinglian.fitness.device.DeviceDtos.BoundDevice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceRepository repository;

    public DeviceController(DeviceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/current")
    public ResponseEntity<BoundDevice> current(HttpServletRequest request) {
        return repository.current(CurrentUser.id(request))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/bind")
    @ResponseStatus(HttpStatus.CREATED)
    public BoundDevice bind(HttpServletRequest request, @Valid @RequestBody BindRequest body) {
        BindResult result = repository.bind(CurrentUser.id(request), body.serialNumber());
        return switch (result.status()) {
            case BOUND -> result.device();
            case NOT_FOUND -> throw new ApiException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "没有找到该设备，请检查 SN 码");
            case ALREADY_BOUND -> throw new ApiException(HttpStatus.CONFLICT, "DEVICE_ALREADY_BOUND", "该设备已绑定其他账号");
            case INVALID -> throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_BINDING_INVALID", "请输入设备 SN 码");
        };
    }

    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbind(HttpServletRequest request) {
        if (!repository.unbind(CurrentUser.id(request))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DEVICE_NOT_BOUND", "当前账号尚未绑定设备");
        }
    }
}
