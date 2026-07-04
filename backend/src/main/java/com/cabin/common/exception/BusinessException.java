package com.cabin.common.exception;

import com.cabin.common.response.ErrorDetail;
import com.cabin.common.response.ResponseCode;
import java.util.List;

public class BusinessException extends RuntimeException {
    private final ResponseCode responseCode;
    private final List<ErrorDetail> details;

    public BusinessException(ResponseCode responseCode) {
        this(responseCode, responseCode.defaultMessage(), List.of());
    }

    public BusinessException(ResponseCode responseCode, String message) {
        this(responseCode, message, List.of());
    }

    public BusinessException(ResponseCode responseCode, String message, List<ErrorDetail> details) {
        super(message);
        this.responseCode = responseCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ResponseCode responseCode() {
        return responseCode;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
