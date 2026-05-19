package com.gov.app.repository;

import java.time.Instant;

public interface CameraDetailProjection {
    Long getCameraId();
    double getLatitude();
    double getLongitude();
    String getLocationName();
    String getExpressway();
    String getResolution();
    String getImageUrl();
    Instant getSnapshotTimestamp();
    String getCongestion();
    String getVehicleDensity();
    String getIncidents();
    String getWeather();
    String getRoadSurface();
    String getSummary();
    Instant getAnalyzedAt();
}
