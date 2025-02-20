package io.github.jockerCN.dao.enums;

import io.github.jockerCN.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@SuppressWarnings("unused")
@AllArgsConstructor
@Getter
public enum LoginMethodEnum implements BaseEnum<LoginMethodEnum, String, String> {

    UP_LOGIN("UP", "用户名和密码登录"),
    CAPTCHA_LOGIN("CAPTCHA", "CAPTCHA"),
    SMS_LOGIN("SMS", "短信验证码登录"),
    EMAIL_LOGIN("EMAIL", "邮箱验证码登录"),
    SOCIAL_LOGIN("SOCIAL_LOGIN", "第三方社交登录 (OAuth2.0)"),
    SINGLE_SIGN_LOGIN("SINGLE", "单点登录"),
    TOKEN_BASED_LOGIN("TOKEN_BASED", "基于Token的登录"),
    OAUTH2_LOGIN("OAUTH2", "OAuth2.0授权登录"),
    DEVICE_BASED_LOGIN("DEVICE_BASED", "基于设备的登录"),
    FINGERPRINT_LOGIN("FINGERPRINT_LOGIN", "指纹登录"),
    FACE_LOGIN("FACE_LOGIN", "人脸识别登录"),
    HARDWARE_TOKEN_LOGIN("HARDWARE_TOKEN", "硬件Token"),
    API_KEY_LOGIN("", "API密钥登录"),
    GEO_LOGIN("GEO_LOGIN", "基于地理位置的登录"),
    BEHAVIOR_LOGIN("", "行为识别登录");

    private final String value;
    private final String desc;
}
