package com.cabin.common.exception;

import com.cabin.common.response.ErrorDetail;
import com.cabin.common.response.Response;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response<Void>> handleBusinessException(BusinessException exception) {
        return build(exception.responseCode(), exception.getMessage(), exception.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<ErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDetail(error.getField(), resolveReason(error.getDefaultMessage())))
                .toList();
        return build(ResponseCode.VALIDATION_ERROR, ResponseCode.VALIDATION_ERROR.defaultMessage(), details);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Response<Void>> handleBind(BindException exception) {
        List<ErrorDetail> details = exception.getFieldErrors().stream()
                .map(error -> new ErrorDetail(error.getField(), resolveReason(error.getDefaultMessage())))
                .toList();
        return build(ResponseCode.VALIDATION_ERROR, ResponseCode.VALIDATION_ERROR.defaultMessage(), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Response<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        List<ErrorDetail> details = exception.getConstraintViolations().stream()
                .map(violation -> new ErrorDetail(
                        violation.getPropertyPath().toString(),
                        resolveReason(violation.getMessage())
                ))
                .toList();
        return build(ResponseCode.VALIDATION_ERROR, ResponseCode.VALIDATION_ERROR.defaultMessage(), details);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Response<Void>> handleBadRequest(Exception exception) {
        return build(ResponseCode.VALIDATION_ERROR, exception.getMessage(), List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Response<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return build(ResponseCode.FILE_TOO_LARGE, ResponseCode.FILE_TOO_LARGE.defaultMessage(), List.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Response<Void>> handleDataAccess(DataAccessException exception) {
        return build(ResponseCode.DATABASE_UNAVAILABLE, ResponseCode.DATABASE_UNAVAILABLE.defaultMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleUnexpected(Exception exception) {
        return build(ResponseCode.INTERNAL_ERROR, ResponseCode.INTERNAL_ERROR.defaultMessage(), List.of());
    }

    private ResponseEntity<Response<Void>> build(
            ResponseCode responseCode,
            String message,
            List<ErrorDetail> details
    ) {
        Response<Void> body = Response.error(responseCode, message, details, TraceContext.currentTraceId());
        return ResponseEntity.status(responseCode.httpStatus()).body(body);
    }

    private String resolveReason(String reason) {
        return reason == null || reason.isBlank() ? "invalid" : reason;
    }
}
