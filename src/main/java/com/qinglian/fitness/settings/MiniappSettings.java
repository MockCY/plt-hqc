package com.qinglian.fitness.settings;

import jakarta.validation.constraints.*;

/** 小程序展示设置，携带乐观锁版本以防覆盖其他管理员的更新。 */
public class MiniappSettings {
    /** 独立维护的版本号，最长 32 个字符；null 表示未配置。 */
    @Size(max = 32)
    @Pattern(regexp = "[A-Za-z0-9._+ -]*", message = "版本号仅支持字母、数字、点、下划线、加号和连字符")
    private String versionNumber;

    /** 版本更新内容，保留换行；null 表示未配置，最长 4000 个字符。 */
    @Size(max = 4000)
    private String updateContent;

    /** 是否传入版本号；旧客户端省略此字段时保留原配置。 */
    private boolean versionNumberSpecified;

    /** 是否传入更新内容；省略与显式清空分别处理。 */
    private boolean updateContentSpecified;

    /** 当前版本号，用于个人中心展示；null 或空白表示未配置，最长 32 个字符。 */
    @Size(max = 32)
    @Pattern(regexp = "[A-Za-z0-9._+ -]*", message = "版本号仅支持字母、数字、点、下划线、加号和连字符")
    private String appVersion;

    /** 读取时的配置版本，保存时须原样传回。 */
    @NotNull
    @Min(0)
    private Long revision;

    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String value) { versionNumber = value; versionNumberSpecified = true; }
    public String getUpdateContent() { return updateContent; }
    public void setUpdateContent(String value) { updateContent = value; updateContentSpecified = true; }

    /** 供持久化层区分省略字段与清空字段，不作为 JSON 属性输出。 */
    public boolean hasVersionNumber() { return versionNumberSpecified; }

    /** 供持久化层判断是否需要更新内容。 */
    public boolean hasUpdateContent() { return updateContentSpecified; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String value) { appVersion = value; }
    public Long getRevision() { return revision; }
    public void setRevision(Long value) { revision = value; }
}
