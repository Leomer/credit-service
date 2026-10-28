package com.bank.credit.config;

import com.bank.credit.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Default circuit breaker and timeout settings, read from the config server. */
@Configuration
public class ResilienceConfig {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer(
            @Value("${resilience.timeout-seconds}") long timeoutSeconds,
            @Value("${resilience.sliding-window-size}") int windowSize,
            @Value("${resilience.minimum-calls}") int minimumCalls,
            @Value("${resilience.failure-rate-threshold}") float failureRate,
            @Value("${resilience.wait-open-seconds}") long waitOpenSeconds) {

        CircuitBreakerConfig circuitConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRate)
                .waitDurationInOpenState(Duration.ofSeconds(waitOpenSeconds))
                // business errors (4xx) are not infrastructure failures
                .ignoreExceptions(BusinessException.class)
                .build();

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(timeoutSeconds))
                .build();

        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(circuitConfig)
                .timeLimiterConfig(timeLimiterConfig)
                .build());
    }
}