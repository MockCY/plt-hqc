package com.qinglian.fitness.settings;

import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.mapper.MiniappSettingsMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理展示配置，保留未配置语义并阻止并发覆盖。 */
@Service
public class MiniappSettingsService {
    private final MiniappSettingsMapper mapper;

    public MiniappSettingsService(MiniappSettingsMapper mapper) { this.mapper = mapper; }

    /** 读取配置；缺少初始化记录时明确报错。 */
    public MiniappSettings get() {
        MiniappSettings settings = mapper.find();
        if (settings == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MINIAPP_SETTINGS_NOT_READY", "小程序设置尚未初始化");
        return settings;
    }

    /** 条件保存三个展示字段，空白清空；省略新增字段时保留旧值，冲突不会覆盖新值。 */
    @Transactional
    public MiniappSettings update(MiniappSettings settings) {
        String version = settings.getAppVersion();
        settings.setAppVersion(version == null || version.isBlank() ? null : version.trim());
        if (settings.hasVersionNumber()) settings.setVersionNumber(normalize(settings.getVersionNumber()));
        if (settings.hasUpdateContent()) settings.setUpdateContent(normalize(settings.getUpdateContent()));
        if (mapper.update(settings) != 1) throw new ApiException(HttpStatus.CONFLICT, "MINIAPP_SETTINGS_CONFLICT", "设置已被其他管理员修改，请重新加载后再保存");
        settings.setRevision(settings.getRevision() + 1);
        // 条件更新与回读保持同一短事务，保证返回本次保存的完整配置。
        return get();
    }
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
