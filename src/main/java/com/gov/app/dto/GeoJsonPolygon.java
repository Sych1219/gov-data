package com.gov.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class GeoJsonPolygon {

    @Schema(description = "GeoJSON geometry type, must be \"Polygon\"", example = "Polygon")
    @NotNull
    @Pattern(regexp = "Polygon", message = "type must be \"Polygon\"")
    private String type;

    @Schema(
        description = "Polygon rings: first element is the outer ring, subsequent elements are holes. "
            + "Each ring is a list of [longitude, latitude] positions. The ring must be closed "
            + "(first position == last position) and have at least 4 positions.",
        example = "[[[103.8,1.28],[103.85,1.28],[103.85,1.32],[103.8,1.32],[103.8,1.28]]]"
    )
    @NotEmpty
    private List<List<List<Double>>> coordinates;
}
