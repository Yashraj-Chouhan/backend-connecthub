package com.connecthub.gatewayservice.filter;

import com.connecthub.gatewayservice.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtFilterTest {

    @Test
    void filter_PublicPath_AllowsRequest() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtFilter jwtFilter = new JwtFilter();
        ReflectionTestUtils.setField(jwtFilter, "jwtUtil", jwtUtil);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/auth/login").build());

        jwtFilter.filter(exchange, chain).block();
        verify(chain, times(1)).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void filter_OptionsRequest_AllowsRequest() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtFilter jwtFilter = new JwtFilter();
        ReflectionTestUtils.setField(jwtFilter, "jwtUtil", jwtUtil);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/rooms").build()
        );

        jwtFilter.filter(exchange, chain).block();
        verify(chain, times(1)).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void filter_WebSocketPath_AllowsRequest() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtFilter jwtFilter = new JwtFilter();
        ReflectionTestUtils.setField(jwtFilter, "jwtUtil", jwtUtil);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/info").build()
        );

        jwtFilter.filter(exchange, chain).block();
        verify(chain, times(1)).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void filter_TranscribePath_AllowsRequest() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtFilter jwtFilter = new JwtFilter();
        ReflectionTestUtils.setField(jwtFilter, "jwtUtil", jwtUtil);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/transcribe").build()
        );

        jwtFilter.filter(exchange, chain).block();
        verify(chain, times(1)).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void filter_FallbackPath_AllowsRequest() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtFilter jwtFilter = new JwtFilter();
        ReflectionTestUtils.setField(jwtFilter, "jwtUtil", jwtUtil);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/fallback").build()
        );

        jwtFilter.filter(exchange, chain).block();
        verify(chain, times(1)).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void filter_ActuatorHealthPath_AllowsRequest() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtFilter jwtFilter = new JwtFilter();
        ReflectionTestUtils.setField(jwtFilter, "jwtUtil", jwtUtil);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build()
        );

        jwtFilter.filter(exchange, chain).block();
        verify(chain, times(1)).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void filter_ValidToken_AllowsRequest() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.validateToken("valid.token")).thenReturn(true);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtFilter jwtFilter = new JwtFilter();
        ReflectionTestUtils.setField(jwtFilter, "jwtUtil", jwtUtil);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                        .build()
        );

        jwtFilter.filter(exchange, chain).block();
        verify(chain, times(1)).filter(exchange);
        verify(jwtUtil, times(1)).validateToken("valid.token");
    }

    @Test
    void filter_InvalidToken_ReturnsUnauthorized() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.validateToken("invalid.token")).thenReturn(false);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        JwtFilter jwtFilter = new JwtFilter();
        ReflectionTestUtils.setField(jwtFilter, "jwtUtil", jwtUtil);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token")
                        .build()
        );

        jwtFilter.filter(exchange, chain).block();
        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_NoAuthHeader_ReturnsUnauthorized() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        JwtFilter jwtFilter = new JwtFilter();
        ReflectionTestUtils.setField(jwtFilter, "jwtUtil", jwtUtil);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/protected").build());

        jwtFilter.filter(exchange, chain).block();
        verify(chain, never()).filter(any());
        verifyNoInteractions(jwtUtil);
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
