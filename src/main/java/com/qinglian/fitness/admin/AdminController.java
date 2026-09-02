package com.qinglian.fitness.admin;

import com.qinglian.fitness.admin.AdminDtos.*;
import com.qinglian.fitness.media.MediaStorageService;
import com.qinglian.fitness.media.MediaStorageService.StoredMedia;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAuthService authService;
    private final AdminRepository repository;
    private final MediaStorageService mediaStorage;
    private final DeviceQrCodeService deviceQrCodeService;

    public AdminController(AdminAuthService authService, AdminRepository repository, MediaStorageService mediaStorage,
                           DeviceQrCodeService deviceQrCodeService) {
        this.authService = authService;
        this.repository = repository;
        this.mediaStorage = mediaStorage;
        this.deviceQrCodeService = deviceQrCodeService;
    }

    @PostMapping("/auth/login")
    public AdminAuthService.LoginResult login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return authService.login(body.username(), body.password(), ip(request));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        authService.logout(AdminCurrent.id(request), String.valueOf(request.getAttribute(AdminCurrent.RAW_TOKEN_ATTRIBUTE)), ip(request));
    }

    @GetMapping("/me")
    public AdminProfile me(HttpServletRequest request) {
        return new AdminProfile(AdminCurrent.username(request));
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return repository.dashboard();
    }

    @GetMapping("/users")
    public PageResult<UserRow> users(
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return repository.users(query, page, pageSize);
    }

    @GetMapping("/courses")
    public PageResult<CourseRow> courses(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return repository.courses(query, status, page, pageSize);
    }

    @GetMapping("/courses/{id}")
    public CourseRow course(@PathVariable long id) {
        return repository.course(id);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseRow createCourse(@Valid @RequestBody CourseRequest body, HttpServletRequest request) {
        CourseRow created = repository.createCourse(body);
        audit(request, "CREATE", "COURSE", created.id(), "新增课程：" + created.title());
        return created;
    }

    @PutMapping("/courses/{id}")
    public CourseRow updateCourse(@PathVariable long id, @Valid @RequestBody CourseRequest body, HttpServletRequest request) {
        CourseRow updated = repository.updateCourse(id, body);
        audit(request, "UPDATE", "COURSE", id, "更新课程：" + updated.title());
        return updated;
    }

    @DeleteMapping("/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(@PathVariable long id, HttpServletRequest request) {
        String title = repository.course(id).title();
        repository.deleteCourse(id);
        audit(request, "DELETE", "COURSE", id, "删除课程：" + title);
    }

    @GetMapping("/exercises")
    public PageResult<ExerciseRow> exercises(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "50") int pageSize
    ) {
        return repository.exercises(query, status, page, pageSize);
    }

    @GetMapping("/exercises/{id}")
    public ExerciseRow exercise(@PathVariable long id) {
        return repository.exercise(id);
    }

    @PostMapping("/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseRow createExercise(@Valid @RequestBody ExerciseRequest body, HttpServletRequest request) {
        ExerciseRow created = repository.createExercise(body);
        audit(request, "CREATE", "EXERCISE", created.id(), "新增动作：" + created.name());
        return created;
    }

    @PutMapping("/exercises/{id}")
    public ExerciseRow updateExercise(@PathVariable long id, @Valid @RequestBody ExerciseRequest body, HttpServletRequest request) {
        ExerciseRow updated = repository.updateExercise(id, body);
        audit(request, "UPDATE", "EXERCISE", id, "更新动作：" + updated.name());
        return updated;
    }

    @DeleteMapping("/exercises/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExercise(@PathVariable long id, HttpServletRequest request) {
        String name = repository.exercise(id).name();
        repository.deleteExercise(id);
        audit(request, "DELETE", "EXERCISE", id, "删除动作：" + name);
    }

    @GetMapping("/plans")
    public PageResult<PlanRow> plans(
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return repository.plans(query, page, pageSize);
    }

    @GetMapping("/plans/{id}")
    public PlanRow plan(@PathVariable long id) {
        return repository.plan(id);
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanRow createPlan(@Valid @RequestBody PlanRequest body, HttpServletRequest request) {
        PlanRow created = repository.createPlan(body);
        audit(request, "CREATE", "PLAN", created.id(), "新增训练计划：" + created.title());
        return created;
    }

    @PutMapping("/plans/{id}")
    public PlanRow updatePlan(@PathVariable long id, @Valid @RequestBody PlanRequest body, HttpServletRequest request) {
        PlanRow updated = repository.updatePlan(id, body);
        audit(request, "UPDATE", "PLAN", id, "更新训练计划：" + updated.title());
        return updated;
    }

    @DeleteMapping("/plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable long id, HttpServletRequest request) {
        String title = repository.plan(id).title();
        repository.deletePlan(id);
        audit(request, "DELETE", "PLAN", id, "删除训练计划：" + title);
    }

    @GetMapping("/campaigns")
    public PageResult<CampaignRow> campaigns(
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return repository.campaigns(query, page, pageSize);
    }

    @GetMapping("/campaigns/{id}")
    public CampaignRow campaign(@PathVariable long id) {
        return repository.campaign(id);
    }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignRow createCampaign(@Valid @RequestBody CampaignRequest body, HttpServletRequest request) {
        CampaignRow created = repository.createCampaign(body);
        audit(request, "CREATE", "CAMPAIGN", created.id(), "新增训练营：" + created.title());
        return created;
    }

    @PutMapping("/campaigns/{id}")
    public CampaignRow updateCampaign(@PathVariable long id, @Valid @RequestBody CampaignRequest body, HttpServletRequest request) {
        CampaignRow updated = repository.updateCampaign(id, body);
        audit(request, "UPDATE", "CAMPAIGN", id, "更新训练营：" + updated.title());
        return updated;
    }

    @DeleteMapping("/campaigns/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCampaign(@PathVariable long id, HttpServletRequest request) {
        String title = repository.campaign(id).title();
        repository.deleteCampaign(id);
        audit(request, "DELETE", "CAMPAIGN", id, "删除训练营：" + title);
    }

    @GetMapping("/workouts")
    public PageResult<WorkoutRow> workouts(
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return repository.workouts(query, page, pageSize);
    }

    @GetMapping("/feedback")
    public PageResult<FeedbackRow> feedback(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return repository.feedback(status, page, pageSize);
    }

    @PutMapping("/feedback/{id}/status")
    public FeedbackRow updateFeedback(
        @PathVariable long id,
        @Valid @RequestBody StatusRequest body,
        HttpServletRequest request
    ) {
        FeedbackRow updated = repository.updateFeedbackStatus(id, body.status());
        audit(request, "UPDATE", "FEEDBACK", id, "更新反馈状态：" + updated.status());
        return updated;
    }

    @GetMapping("/audit-logs")
    public PageResult<AuditRow> audits(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return repository.audits(page, pageSize);
    }

    @GetMapping("/devices")
    public PageResult<DeviceRow> devices(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String deviceModel,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return repository.devices(query, deviceModel, page, pageSize);
    }

    @GetMapping("/device-models")
    public java.util.List<DeviceModelRow> deviceModels() {
        return repository.deviceModels();
    }

    @PostMapping("/device-models")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceModelRow createDeviceModel(
        @Valid @RequestBody DeviceModelRequest body,
        HttpServletRequest request
    ) {
        DeviceModelRow created = repository.createDeviceModel(body);
        audit(request, "CREATE", "DEVICE_MODEL", created.id(), "新增设备型号：" + created.name());
        return created;
    }

    @PutMapping("/device-models/{id}")
    public DeviceModelRow updateDeviceModel(
        @PathVariable long id,
        @Valid @RequestBody DeviceModelRequest body,
        HttpServletRequest request
    ) {
        DeviceModelRow updated = repository.updateDeviceModel(id, body);
        audit(request, "UPDATE", "DEVICE_MODEL", id, "更新设备型号：" + updated.name());
        return updated;
    }

    @DeleteMapping("/device-models/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeviceModel(@PathVariable long id, HttpServletRequest request) {
        String name = repository.deviceModel(id).name();
        repository.deleteDeviceModel(id);
        audit(request, "DELETE", "DEVICE_MODEL", id, "删除设备型号：" + name);
    }

    @GetMapping("/devices/{id}")
    public DeviceRow device(@PathVariable long id) {
        return repository.device(id);
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceRow createDevice(@Valid @RequestBody DeviceCreateRequest body, HttpServletRequest request) {
        DeviceRow created = repository.createDevice(body);
        audit(request, "CREATE", "DEVICE", created.id(), "新增设备：" + created.serialNumber());
        return created;
    }

    @DeleteMapping("/devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDevice(@PathVariable long id, HttpServletRequest request) {
        String serialNumber = repository.device(id).serialNumber();
        repository.deleteDevice(id);
        audit(request, "DELETE", "DEVICE", id, "删除设备：" + serialNumber);
    }

    @GetMapping(value = {"/devices/{id}/label", "/devices/{id}/qr-code"}, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> deviceLabel(@PathVariable long id) {
        DeviceRow device = repository.device(id);
        byte[] png = deviceQrCodeService.generateDeviceLabel(device);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + device.serialNumber() + "-label.png\"")
            .contentType(MediaType.IMAGE_PNG)
            .body(png);
    }

    @PostMapping("/media/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public StoredMedia upload(
        @RequestParam MultipartFile file,
        @RequestParam String kind,
        HttpServletRequest request
    ) {
        StoredMedia stored = mediaStorage.store(file, kind);
        audit(request, "UPLOAD", "MEDIA", null, "上传" + ("video".equals(stored.kind()) ? "视频" : "图片") + "：" + stored.originalName());
        return stored;
    }

    private void audit(HttpServletRequest request, String action, String target, Long targetId, String summary) {
        authService.audit(AdminCurrent.id(request), action, target, targetId, summary, ip(request));
    }

    private String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
