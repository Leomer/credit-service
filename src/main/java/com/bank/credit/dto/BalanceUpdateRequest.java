package com.bank.credit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BalanceUpdateRequest {

    @NotNull(message = "amount is required")
    private BigDecimal amount;
}
