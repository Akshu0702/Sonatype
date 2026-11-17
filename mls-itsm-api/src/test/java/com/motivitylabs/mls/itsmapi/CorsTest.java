package com.motivitylabs.mls.itsmapi;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsTest {

    private static CorsConfiguration forRequest(List<String> origins) {
        var source = new SecurityConfig().corsConfigurationSource(new ItsmProperties(List.of("c"), List.of(), "x", 1, origins));
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", "/tickets"));
    }

    @Test
    void noBrowserOriginIsAllowedUnlessOneIsListed() {
        assertThat(forRequest(List.of())).isNull();
    }

    @Test
    void onlyTheListedOriginsMayCallTheApiAndOnlyWithTheHeadersItNeeds() {
        CorsConfiguration cors = forRequest(List.of("https://console.example.com"));
        assertThat(cors.checkOrigin("https://console.example.com")).isEqualTo("https://console.example.com");
        assertThat(cors.checkOrigin("https://evil.example.com")).isNull();
        assertThat(cors.checkHttpMethod(org.springframework.http.HttpMethod.POST)).isNotNull();
        assertThat(cors.checkHttpMethod(org.springframework.http.HttpMethod.DELETE)).isNull();
        assertThat(cors.checkHeaders(List.of("Authorization", "Content-Type"))).isNotNull();
        assertThat(cors.getAllowCredentials()).isNull();
    }
}
