package com.gov.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${gov-api.taxi.base-url}")
    private String baseUrl;

    @Value("${gov-api.taxi.api-key:}")
    private String apiKey;

    @Bean
    public WebClient taxiWebClient(WebClient.Builder builder) {
        WebClient.Builder b = builder.baseUrl(baseUrl);
        if (StringUtils.hasText(apiKey)) {
            b = b.defaultHeader("x-api-key", apiKey);
        }
        return b.build();
    }
}
