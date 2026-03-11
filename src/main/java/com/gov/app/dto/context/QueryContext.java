package com.gov.app.dto.context;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RadiusContext.class,  name = "radius"),
    @JsonSubTypes.Type(value = NearestContext.class, name = "nearest"),
    @JsonSubTypes.Type(value = ZoneContext.class,    name = "zone"),
    @JsonSubTypes.Type(value = PolygonContext.class, name = "polygon"),
    @JsonSubTypes.Type(value = RoadContext.class,    name = "road"),
    @JsonSubTypes.Type(value = RouteContext.class,   name = "route"),
})
public sealed interface QueryContext
        permits RadiusContext, NearestContext, ZoneContext, PolygonContext, RoadContext, RouteContext {}
