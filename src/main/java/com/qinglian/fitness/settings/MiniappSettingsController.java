package com.qinglian.fitness.settings;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 提供公开配置读取和管理员编辑；写接口由现有管理员拦截器鉴权。 */
@RestController
public class MiniappSettingsController {
    private final MiniappSettingsService service;

    public MiniappSettingsController(MiniappSettingsService service) { this.service = service; }

    /** 读取展示设置，禁用缓存以便及时同步管理员修改。 */
    @GetMapping({"/api/miniapp-settings", "/api/admin/miniapp-settings"})
    public ResponseEntity<MiniappSettings> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get());
    }

    /** 管理员保存设置，revision 必须匹配读取时的版本。 */
    @PutMapping("/api/admin/miniapp-settings")
    public MiniappSettings update(@Valid @RequestBody MiniappSettings settings) {
        return service.update(settings);
    }
}
