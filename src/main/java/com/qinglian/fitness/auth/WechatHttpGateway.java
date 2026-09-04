package com.qinglian.fitness.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.config.WechatProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

@Service
public class WechatHttpGateway implements WechatGateway {

    private final WechatProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Object accessTokenLock = new Object();
    private volatile CachedAccessToken cachedAccessToken;

    public WechatHttpGateway(
        WechatProperties properties,
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.apiBaseUrl()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public WechatIdentity exchangeLoginCode(String loginCode) {
        requireConfigured();
        String responseBody = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/sns/jscode2session")
                .queryParam("appid", properties.appId())
                .queryParam("secret", properties.appSecret())
                .queryParam("js_code", loginCode)
                .queryParam("grant_type", "authorization_code")
                .build())
            .retrieve()
            .body(String.class);
        CodeSessionResponse response = parseResponse(responseBody, CodeSessionResponse.class);

        if (response == null || response.errcode() != null || response.openid() == null) {
            throw wechatError("WECHAT_LOGIN_FAILED", response == null ? null : response.errmsg());
        }
        return new WechatIdentity(response.openid(), response.unionid());
    }

    @Override
    public WechatPhone exchangePhoneCode(String phoneCode) {
        requireConfigured();
        String requestBody = toJson(Map.of("code", phoneCode));
        String responseBody = restClient.post()
            .uri(uriBuilder -> uriBuilder.path("/wxa/business/getuserphonenumber")
                .queryParam("access_token", accessToken())
                .build())
            .contentType(MediaType.APPLICATION_JSON)
            .contentLength(requestBody.getBytes(StandardCharsets.UTF_8).length)
            .body(requestBody)
            .retrieve()
            .body(String.class);
        PhoneResponse response = parseResponse(responseBody, PhoneResponse.class);

        if (response == null || response.errcode() != 0 || response.phoneInfo() == null) {
            throw wechatError("WECHAT_PHONE_FAILED", response == null ? null : response.errmsg());
        }
        PhoneInfo phone = response.phoneInfo();
        return new WechatPhone(phone.phoneNumber(), phone.purePhoneNumber(), phone.countryCode());
    }

    @Override
    public byte[] getDeviceBindingMiniProgramCode() {
        requireConfigured();
        String requestBody = toJson(Map.of(
            "scene", "device-bind",
            "env_version", properties.resolvedMiniProgramCodeEnvVersion(),
            "width", 430
        ));
        byte[] responseBody = restClient.post()
            .uri(uriBuilder -> uriBuilder.path("/wxa/getwxacodeunlimit")
                .queryParam("access_token", accessToken())
                .build())
            .contentType(MediaType.APPLICATION_JSON)
            .contentLength(requestBody.getBytes(StandardCharsets.UTF_8).length)
            .body(requestBody)
            .retrieve()
            .body(byte[].class);

        if (responseBody == null || responseBody.length == 0) {
            throw wechatError("WECHAT_MINI_PROGRAM_CODE_FAILED", null);
        }
        if (isJsonResponse(responseBody)) {
            WechatErrorResponse response = parseResponse(
                new String(responseBody, StandardCharsets.UTF_8),
                WechatErrorResponse.class
            );
            throw wechatError(
                "WECHAT_MINI_PROGRAM_CODE_FAILED",
                response == null ? null : response.errmsg()
            );
        }
        if (!isSupportedImage(responseBody)) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "WECHAT_INVALID_RESPONSE",
                "微信接口没有返回有效的小程序码图片"
            );
        }
        return responseBody;
    }

    private String accessToken() {
        CachedAccessToken current = cachedAccessToken;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(300))) {
            return current.value();
        }
        synchronized (accessTokenLock) {
            current = cachedAccessToken;
            if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(300))) {
                return current.value();
            }
            String responseBody = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/cgi-bin/token")
                    .queryParam("grant_type", "client_credential")
                    .queryParam("appid", properties.appId())
                    .queryParam("secret", properties.appSecret())
                    .build())
                .retrieve()
                .body(String.class);
            AccessTokenResponse response = parseResponse(responseBody, AccessTokenResponse.class);
            if (response == null || response.errcode() != null || response.accessToken() == null) {
                throw wechatError("WECHAT_ACCESS_TOKEN_FAILED", response == null ? null : response.errmsg());
            }
            cachedAccessToken = new CachedAccessToken(
                response.accessToken(),
                Instant.now().plusSeconds(response.expiresIn() == null ? 7200 : response.expiresIn())
            );
            return cachedAccessToken.value();
        }
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "WECHAT_NOT_CONFIGURED",
                "服务器尚未配置微信小程序 AppID 和 AppSecret"
            );
        }
    }

    private ApiException wechatError(String code, String detail) {
        String message = detail == null || detail.isBlank()
            ? "微信接口调用失败"
            : "微信接口调用失败：" + detail;
        return new ApiException(HttpStatus.BAD_GATEWAY, code, message);
    }

    private <T> T parseResponse(String responseBody, Class<T> responseType) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JacksonException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "WECHAT_INVALID_RESPONSE",
                "微信接口返回了无法解析的数据"
            );
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法生成微信接口请求数据", exception);
        }
    }

    private boolean isJsonResponse(byte[] responseBody) {
        for (byte value : responseBody) {
            if (!Character.isWhitespace(value)) {
                return value == '{';
            }
        }
        return false;
    }

    private boolean isSupportedImage(byte[] responseBody) {
        return isPng(responseBody) || isJpeg(responseBody);
    }

    private boolean isPng(byte[] responseBody) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (responseBody.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (responseBody[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isJpeg(byte[] responseBody) {
        return responseBody.length >= 3
            && responseBody[0] == (byte) 0xff
            && responseBody[1] == (byte) 0xd8
            && responseBody[2] == (byte) 0xff;
    }

    private record CachedAccessToken(String value, Instant expiresAt) {
    }

    private record CodeSessionResponse(
        String openid,
        String unionid,
        String sessionKey,
        Integer errcode,
        String errmsg
    ) {
        private CodeSessionResponse(
            @JsonProperty("openid") String openid,
            @JsonProperty("unionid") String unionid,
            @JsonProperty("session_key") String sessionKey,
            @JsonProperty("errcode") Integer errcode,
            @JsonProperty("errmsg") String errmsg
        ) {
            this.openid = openid;
            this.unionid = unionid;
            this.sessionKey = sessionKey;
            this.errcode = errcode;
            this.errmsg = errmsg;
        }
    }

    private record AccessTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Integer expiresIn,
        Integer errcode,
        String errmsg
    ) {
    }

    private record PhoneResponse(
        int errcode,
        String errmsg,
        @JsonProperty("phone_info") PhoneInfo phoneInfo
    ) {
    }

    private record PhoneInfo(
        String phoneNumber,
        String purePhoneNumber,
        String countryCode
    ) {
        private PhoneInfo(
            @JsonProperty("phoneNumber") String phoneNumber,
            @JsonProperty("purePhoneNumber") String purePhoneNumber,
            @JsonProperty("countryCode") String countryCode
        ) {
            this.phoneNumber = phoneNumber;
            this.purePhoneNumber = purePhoneNumber;
            this.countryCode = countryCode;
        }
    }

    private record WechatErrorResponse(Integer errcode, String errmsg) {
    }
}
