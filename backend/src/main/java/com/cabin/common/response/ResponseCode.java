package com.cabin.common.response;

import org.springframework.http.HttpStatus;

public enum ResponseCode {
    OK(HttpStatus.OK, "success"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    UNSUPPORTED_DATA_TYPE(HttpStatus.BAD_REQUEST, "当前接口不支持该数据类型"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "管理员账号已禁用"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "资源状态冲突"),
    RECORD_ALREADY_DELETED(HttpStatus.CONFLICT, "记录已经软删除"),
    RECORD_NOT_DELETED(HttpStatus.CONFLICT, "记录未删除，不能恢复"),
    FILE_EXPIRED(HttpStatus.GONE, "文件已过期"),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "文件超过大小限制"),
    IMPORT_SCHEMA_ERROR(HttpStatus.UNPROCESSABLE_CONTENT, "CSV 表头或数据类型与模板不一致"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误"),
    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "数据库暂不可用");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ResponseCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
