package com.bank.credit.mapper;

import com.bank.credit.dto.CreditDto;
import com.bank.credit.model.Credit;

import java.math.BigDecimal;

public final class CreditMapper {

    private CreditMapper() {
    }

    public static CreditDto toDto(Credit e) {
        return CreditDto.builder()
                .id(e.getId())
                .creditNumber(e.getCreditNumber())
                .creditType(e.getCreditType())
                .customerDocument(e.getCustomerDocument())
                .customerType(e.getCustomerType())
                .creditLimit(e.getCreditLimit())
                .balance(e.getBalance())
                .dueDate(e.getDueDate())
                .build();
    }

    public static Credit toEntity(CreditDto d) {
        return Credit.builder()
                .id(d.getId())
                .creditNumber(d.getCreditNumber())
                .creditType(d.getCreditType())
                .customerDocument(d.getCustomerDocument())
                .customerType(d.getCustomerType())
                .creditLimit(d.getCreditLimit())
                .balance(d.getBalance() != null ? d.getBalance() : BigDecimal.ZERO)
                .dueDate(d.getDueDate())
                .build();
    }
}
