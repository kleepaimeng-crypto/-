package com.cabin.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.cabin.common.response.ErrorDetail;
import com.cabin.common.response.ResponseCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessExceptionTests {
    @Test
    void storesResponseCodeMessageAndDetails() {
        ErrorDetail detail = new ErrorDetail("tagId", "not_found");

        BusinessException exception = new BusinessException(
                ResponseCode.RESOURCE_NOT_FOUND,
                "标签不存在",
                List.of(detail)
        );

        assertThat(exception.responseCode()).isEqualTo(ResponseCode.RESOURCE_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("标签不存在");
        assertThat(exception.details()).containsExactly(detail);
    }
}
