package com.restaurantapp.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ChatConfiguration {
    @Bean("openRouterRestClient")
    RestClient openRouterRestClient(RestClient.Builder builder,
            @Value("${openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
