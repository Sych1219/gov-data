package com.gov.app.dto.upstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GovTrafficImageResponse {

    private List<Item> items;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String timestamp;
        private List<Camera> cameras;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Camera {
        private String timestamp;

        @JsonProperty("camera_id")
        private String cameraId;

        private String image;

        private Location location;

        @JsonProperty("image_metadata")
        private ImageMetadata imageMetadata;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Location {
        private double latitude;
        private double longitude;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageMetadata {
        private int height;
        private int width;
        private String md5;
    }
}
