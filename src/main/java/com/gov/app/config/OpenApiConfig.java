package com.gov.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Taxi Availability API")
                        .version("v1")
                        .description("""
                                Real-time and historical Singapore taxi availability.
                                All datetime parameters accept ISO-8601 in SGT (UTC+8).
                                Latest snapshot is used when datetime is omitted.
                                """)
                        .contact(new Contact()
                                .name("Gov Data Platform")
                                .email("api-support@gov.sg"))
                        .license(new License()
                                .name("Singapore Open Data Licence")
                                .url("https://data.gov.sg/open-data-licence")))
                .components(new Components()
                        // Reusable error schema — referenced via $ref in @ApiResponse across controllers
                        .addSchemas("ErrorResponse", buildErrorSchema()));
    }

    /**
     * Defines the canonical error envelope produced by GlobalExceptionHandler.
     * Declared once here and referenced by $ref in controller @ApiResponse annotations.
     */
    private Schema<?> buildErrorSchema() {
        return new ObjectSchema()
                .description("Standard error response")
                .addProperty("status",  new IntegerSchema().example(400))
                .addProperty("error",   new StringSchema().example("Bad Request"))
                .addProperty("message", new StringSchema().example("Validation failed"))
                .addProperty("errors",  new MapSchema().additionalProperties(new StringSchema())
                        .description("Field-level validation errors, present only on 400"));
    }
}
