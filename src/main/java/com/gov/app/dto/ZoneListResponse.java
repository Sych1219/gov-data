package com.gov.app.dto;

import java.util.List;

public record ZoneListResponse(List<ZoneEntry> zones) {

    public record ZoneEntry(String name, String category) {}
}
