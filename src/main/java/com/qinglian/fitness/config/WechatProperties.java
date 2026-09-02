package com.qinglian.fitness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat")
public record WechatProperties(
    String appId,
    String appSecret,
    String apiBaseUrl,
    String miniProgramCodeEnvVersion
) {

    public boolean configured() {
        return appId != null && !appId.isBlank()
            && appSecret != null && !appSecret.isBlank();
    }

    public String resolvedMiniProgramCodeEnvVersion() {
        return miniProgramCodeEnvVersion == null || miniProgramCodeEnvVersion.isBlank()
            ? "release"
            : miniProgramCodeEnvVersion;
    }
}
