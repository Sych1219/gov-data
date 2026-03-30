package com.gov.app.dto.context;

import com.gov.app.dto.GeoJsonPolygon;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public final class PolygonContext implements QueryContext {
    GeoJsonPolygon polygon;
}
