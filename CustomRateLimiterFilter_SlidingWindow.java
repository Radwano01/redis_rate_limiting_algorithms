package com.example.smart_fuel_management_system;

import lombok.AllArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

@Component
@AllArgsConstructor
public class CustomRateLimiterFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final int LIMIT = 10; // requests
    private static final Duration WINDOW = Duration.ofSeconds(60);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String ip = extractIp(exchange);
        String key = "sliding_window:" + ip;

        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW.toMillis();

        Range<Double> range = Range.closed(Double.NEGATIVE_INFINITY, (double) windowStart);

        return redisTemplate.opsForZSet()
                // 1. remove old requests
                .removeRangeByScore(key, range)

                // 2. count remaining requests
                .then(redisTemplate.opsForZSet().size(key))

                .flatMap(count -> {

                    if (count != null && count >= LIMIT) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }

                    // 3. add current request
                    return redisTemplate.opsForZSet()
                            .add(key, String.valueOf(now), now)
                            .then(chain.filter(exchange));
                });
    }

    private String extractIp(ServerWebExchange exchange) {

        String ip = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For");

        if (ip == null || ip.isEmpty()) {
            ip = exchange.getRequest()
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress();
        }

        return ip.split(",")[0].trim();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
