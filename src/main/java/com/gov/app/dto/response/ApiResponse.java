package com.gov.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ApiResponse<T> {

    boolean success;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    ApiError error;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return ApiResponse.<T>builder().success(false).error(error).build();
    }
}
