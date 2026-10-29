package com.bank.credit.service;

import com.bank.credit.dto.CreditDto;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.math.BigDecimal;

public interface CreditService {

    Single<CreditDto> create(CreditDto dto);
    Flowable<CreditDto> findAll();
    Single<CreditDto> findByCreditNumber(String creditNumber);
    Flowable<CreditDto> findByCustomer(String customerDocument);
    Single<CreditDto> update(String creditNumber, CreditDto dto);
    Single<CreditDto> updateBalance(String creditNumber, BigDecimal amount);
    Completable delete(String creditNumber);
    Single<Boolean> hasOverdueDebt(String customerDocument);
}
