package com.gov.app.dto.context;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public final class NearestContext implements QueryContext {
    double lat;
    double lon;
    int limit;
}
