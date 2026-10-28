package com.bank.credit.service.impl;

import com.bank.credit.client.CustomerClient;
import com.bank.credit.dto.CreditDto;
import com.bank.credit.dto.CustomerResponse;
import com.bank.credit.exception.BusinessException;
import com.bank.credit.exception.NotFoundException;
import com.bank.credit.model.Credit;
import com.bank.credit.model.CreditType;
import com.bank.credit.repository.CreditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link CreditServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class CreditServiceImplTest {

    @Mock
    private CreditRepository repository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    @InjectMocks
    private CreditServiceImpl service;

    // ---------- helpers ----------

    private CustomerResponse customer(String type) {
        CustomerResponse c = new CustomerResponse();
        c.setDocumentNumber("123");
        c.setType(type);
        return c;
    }

    private CreditDto request(CreditType type) {
        return CreditDto.builder().creditType(type).customerDocument("123")
                .creditLimit(new BigDecimal("5000")).build();
    }

    private Credit credit(String balance, String limit) {
        return Credit.builder().creditNumber("CR1").creditType(CreditType.CREDIT_CARD)
                .customerDocument("123").balance(new BigDecimal(balance))
                .creditLimit(new BigDecimal(limit)).build();
    }

    private void saveReturnsSameEntity() {
        when(repository.save(any(Credit.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
    }

    private void atomicUpdateReturns(Mono<Credit> result) {
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Credit.class))).thenReturn(result);
    }

    // ---------- create ----------

    @Test
    void createPersonalCreditShouldSucceedWhenCustomerHasNone() {
        when(customerClient.findByDocument("123")).thenReturn(Mono.just(customer("PERSONAL")));
        when(repository.countByCustomerDocumentAndCreditType("123", CreditType.PERSONAL))
                .thenReturn(Mono.just(0L));
        saveReturnsSameEntity();

        service.create(request(CreditType.PERSONAL))
                .test()
                .assertValue(c -> c.getCreditNumber().startsWith("CR")
                        && BigDecimal.ZERO.compareTo(c.getBalance()) == 0)
                .assertComplete();
    }

    @Test
    void createPersonalCreditShouldFailWhenCustomerAlreadyHasOne() {
        when(customerClient.findByDocument("123")).thenReturn(Mono.just(customer("PERSONAL")));
        when(repository.countByCustomerDocumentAndCreditType("123", CreditType.PERSONAL))
                .thenReturn(Mono.just(1L));

        service.create(request(CreditType.PERSONAL)).test().assertError(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createPersonalCreditShouldFailForBusinessCustomer() {
        when(customerClient.findByDocument("123")).thenReturn(Mono.just(customer("BUSINESS")));

        service.create(request(CreditType.PERSONAL)).test().assertError(BusinessException.class);
    }

    @Test
    void createBusinessCreditShouldFailForPersonalCustomer() {
        when(customerClient.findByDocument("123")).thenReturn(Mono.just(customer("PERSONAL")));

        service.create(request(CreditType.BUSINESS)).test().assertError(BusinessException.class);
    }

    @Test
    void createBusinessCreditShouldSucceedWithoutCountingExisting() {
        when(customerClient.findByDocument("123")).thenReturn(Mono.just(customer("BUSINESS")));
        saveReturnsSameEntity();

        service.create(request(CreditType.BUSINESS)).test().assertComplete();

        verify(repository, never()).countByCustomerDocumentAndCreditType(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PERSONAL", "BUSINESS"})
    void createCreditCardShouldSucceedForAnyCustomerType(String customerType) {
        when(customerClient.findByDocument("123")).thenReturn(Mono.just(customer(customerType)));
        saveReturnsSameEntity();

        service.create(request(CreditType.CREDIT_CARD)).test().assertComplete();
    }

    @Test
    void createShouldFailWhenCustomerDoesNotExist() {
        when(customerClient.findByDocument("123"))
                .thenReturn(Mono.error(new BusinessException("Customer not found")));

        service.create(request(CreditType.CREDIT_CARD)).test().assertError(BusinessException.class);
        verify(repository, never()).save(any());
    }

    // ---------- queries ----------

    @Test
    void findAllShouldReturnAllCredits() {
        when(repository.findAll()).thenReturn(Flux.just(credit("0", "100"), credit("0", "100")));
        service.findAll().test().assertValueCount(2).assertComplete();
    }

    @Test
    void findByCustomerShouldReturnCustomerCredits() {
        when(repository.findByCustomerDocument("123")).thenReturn(Flux.just(credit("0", "100")));
        service.findByCustomer("123").test().assertValueCount(1).assertComplete();
    }

    @Test
    void findByCreditNumberShouldFailWhenMissing() {
        when(repository.findByCreditNumber("X")).thenReturn(Mono.empty());
        service.findByCreditNumber("X").test().assertError(NotFoundException.class);
    }

    @Test
    void findByCreditNumberShouldReturnCredit() {
        when(repository.findByCreditNumber("CR1")).thenReturn(Mono.just(credit("0", "100")));
        service.findByCreditNumber("CR1")
                .test()
                .assertValue(c -> "CR1".equals(c.getCreditNumber()))
                .assertComplete();
    }

    // ---------- update / delete ----------

    @Test
    void updateShouldChangeLimitWhenAboveBalance() {
        when(repository.findByCreditNumber("CR1")).thenReturn(Mono.just(credit("500", "1000")));
        saveReturnsSameEntity();

        CreditDto dto = CreditDto.builder().creditLimit(new BigDecimal("2000")).build();

        service.update("CR1", dto)
                .test()
                .assertValue(c -> new BigDecimal("2000").compareTo(c.getCreditLimit()) == 0)
                .assertComplete();
    }

    @Test
    void updateShouldFailWhenNewLimitIsBelowBalance() {
        when(repository.findByCreditNumber("CR1")).thenReturn(Mono.just(credit("500", "1000")));

        CreditDto dto = CreditDto.builder().creditLimit(new BigDecimal("300")).build();

        service.update("CR1", dto).test().assertError(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteShouldRemoveExistingCredit() {
        Credit c = credit("0", "100");
        when(repository.findByCreditNumber("CR1")).thenReturn(Mono.just(c));
        when(repository.delete(c)).thenReturn(Mono.empty());

        service.delete("CR1").test().assertComplete();
        verify(repository).delete(c);
    }

    @Test
    void deleteShouldFailWhenMissing() {
        when(repository.findByCreditNumber("X")).thenReturn(Mono.empty());
        service.delete("X").test().assertError(NotFoundException.class);
    }

    // ---------- balance ----------

    @Test
    void chargeShouldIncreaseBalance() {
        when(repository.findByCreditNumber("CR1")).thenReturn(Mono.just(credit("0", "1000")));
        atomicUpdateReturns(Mono.just(credit("300", "1000")));

        service.updateBalance("CR1", new BigDecimal("300"))
                .test()
                .assertValue(c -> new BigDecimal("300").compareTo(c.getBalance()) == 0)
                .assertComplete();
    }

    @Test
    void chargeAboveLimitShouldFailWithoutTouchingDatabase() {
        when(repository.findByCreditNumber("CR1")).thenReturn(Mono.just(credit("0", "1000")));

        service.updateBalance("CR1", new BigDecimal("1500"))
                .test()
                .assertError(BusinessException.class);

        verify(mongoTemplate, never()).findAndModify(any(Query.class),
                any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Credit.class));
    }

    @Test
    void chargeShouldFailWhenAtomicGuardRejects() {
        when(repository.findByCreditNumber("CR1")).thenReturn(Mono.just(credit("900", "1000")));
        atomicUpdateReturns(Mono.empty());

        service.updateBalance("CR1", new BigDecimal("200"))
                .test()
                .assertError(BusinessException.class);
    }

    @Test
    void paymentShouldDecreaseBalance() {
        when(repository.findByCreditNumber("CR1")).thenReturn(Mono.just(credit("300", "1000")));
        atomicUpdateReturns(Mono.just(credit("200", "1000")));

        service.updateBalance("CR1", new BigDecimal("-100"))
                .test()
                .assertValue(c -> new BigDecimal("200").compareTo(c.getBalance()) == 0)
                .assertComplete();
    }

    @Test
    void paymentAboveDebtShouldFail() {
        when(repository.findByCreditNumber("CR1")).thenReturn(Mono.just(credit("100", "1000")));
        atomicUpdateReturns(Mono.empty());

        service.updateBalance("CR1", new BigDecimal("-500"))
                .test()
                .assertError(BusinessException.class);
    }
}