package com.gov.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {

    @Value("${gov-api.taxi.base-url}")
    private String baseUrl;

    @Value("${gov-api.taxi.api-key:}")
    private String apiKey;

    @Value("${gov-api.traffic-image.base-url}")
    private String trafficImageBaseUrl;

    @Value("${gov-api.civic-app.base-url}")
    private String civicAppBaseUrl;

    @Bean
    public RestClient taxiWebClient(RestClient.Builder builder) {
        RestClient.Builder b = builder.baseUrl(baseUrl);
        if (StringUtils.hasText(apiKey)) {
            b = b.defaultHeader("x-api-key", apiKey);
        }
        return b.build();
    }

    @Bean
    public RestClient trafficImageWebClient(RestClient.Builder builder) {
        return builder.baseUrl(trafficImageBaseUrl).build();
    }

    @Bean
    public RestClient civicAppWebClient(RestClient.Builder builder) {
        return builder.baseUrl(civicAppBaseUrl).build();
    }
}
