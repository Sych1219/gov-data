package com.gov.app.dto.request;

import lombok.Data;

/** PATCH payload — null fields are left unchanged. */
@Data
public class UpdateHintRequest {

    private String  status;
    private Integer seenCount;
}
