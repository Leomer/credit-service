package com.bank.credit.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests that the customer cache is used before calling customer-service. */
@ExtendWith(MockitoExtension.class)
class CustomerClientCacheTest {

    @Mock
    private WebClient webClient;

    @Mock
    @SuppressWarnings("rawtypes")
    private ReactiveCircuitBreakerFactory circuitBreakerFactory;

    @Mock
    private ReactiveStringRedisTemplate redis;

    @Mock
    private ReactiveValueOperations<String, String> ops;

    @Test
    @SuppressWarnings("unchecked")
    void findByDocumentShouldReturnCachedCustomerWithoutCallingService() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("customer:123")).thenReturn(Mono.just("123|PERSONAL|VIP"));

        CustomerClient client = new CustomerClient(webClient, circuitBreakerFactory, redis, 10);

        client.findByDocument("123")
                .as(m -> io.reactivex.rxjava3.core.Single.fromPublisher(m))
                .test()
                .assertValue(c -> "PERSONAL".equals(c.getType()) && "VIP".equals(c.getProfile()))
                .assertComplete();

        verify(webClient, never()).get();
    }
}