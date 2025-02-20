package io.github.jockerCN.dao.enums;

import io.github.jockerCN.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum HttpMethodEnum implements BaseEnum<HttpMethodEnum, String, String> {
    GET("GET", ""),
    HEAD("HEAD", ""),
    POST("POST", ""),
    PUT("PUT", ""),
    PATCH("PATCH", ""),
    DELETE("DELETE", ""),
    OPTIONS("OPTIONS", ""),
    TRACE("TRACE", ""),
    NO("NO", ""),
    ;
    private final String value;
    private final String desc;

}