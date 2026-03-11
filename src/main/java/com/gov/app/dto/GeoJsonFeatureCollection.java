package com.gov.app.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class GeoJsonFeatureCollection {

    String type = "FeatureCollection";

    List<GeoJsonFeature> features;
}
