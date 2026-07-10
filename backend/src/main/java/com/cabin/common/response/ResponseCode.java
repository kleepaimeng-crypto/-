package com.cabin.common.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ResponseCode {
    OK(HttpStatus.OK),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_DATA_TYPE(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT),
    RECORD_ALREADY_DELETED(HttpStatus.CONFLICT),
    RECORD_NOT_DELETED(HttpStatus.CONFLICT),
    FILE_EXPIRED(HttpStatus.GONE),
    FILE_TOO_LARGE(HttpStatusCode.valueOf(413)),
    IMPORT_SCHEMA_ERROR(HttpStatusCode.valueOf(422)),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatusCode httpStatus;

    ResponseCode(HttpStatusCode httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatusCode httpStatus() {
        return httpStatus;
    }
}
