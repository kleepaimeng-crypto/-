package com.cabin.common.exception;

import com.cabin.common.response.ErrorDetail;
import com.cabin.common.response.Response;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response<Void>> handleBusinessException(BusinessException exception) {
        ResponseCode code = exception.responseCode();
        List<ErrorDetail> details = exception.details().isEmpty() ? null : exception.details();
        return ResponseEntity.status(code.httpStatus())
                .body(Response.error(code, exception.getMessage(), details, TraceContext.currentTraceId()));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Response<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.status(ResponseCode.VALIDATION_ERROR.httpStatus())
                .body(Response.error(
                        ResponseCode.VALIDATION_ERROR,
                        exception.getMessage(),
                        TraceContext.currentTraceId()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> handleValidation(MethodArgumentNotValidException exception) {
        List<ErrorDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toErrorDetail)
                .toList();
        return ResponseEntity.status(ResponseCode.VALIDATION_ERROR.httpStatus())
                .body(Response.error(
                        ResponseCode.VALIDATION_ERROR,
                        "参数校验失败",
                        details,
                        TraceContext.currentTraceId()
                ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Response<Void>> handleDataAccess() {
        return ResponseEntity.status(ResponseCode.DATABASE_UNAVAILABLE.httpStatus())
                .body(Response.error(
                        ResponseCode.DATABASE_UNAVAILABLE,
                        "数据库暂不可用",
                        TraceContext.currentTraceId()
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Response<Void>> handleResourceNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(ResponseCode.RESOURCE_NOT_FOUND.httpStatus())
                .body(Response.error(
                        ResponseCode.RESOURCE_NOT_FOUND,
                        "请求的资源不存在",
                        TraceContext.currentTraceId()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleUnexpected(Exception exception) {
        log.error("未处理的服务异常，traceId={}", TraceContext.currentTraceId(), exception);
        return ResponseEntity.status(ResponseCode.INTERNAL_ERROR.httpStatus())
                .body(Response.error(
                        ResponseCode.INTERNAL_ERROR,
                        "服务内部错误",
                        TraceContext.currentTraceId()
                ));
    }

    private ErrorDetail toErrorDetail(FieldError error) {
        String reason = error.getDefaultMessage();
        return new ErrorDetail(error.getField(), reason == null ? "invalid" : reason);
    }
}
