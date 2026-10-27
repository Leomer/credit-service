package com.bank.credit.dto;

import com.bank.credit.model.CreditType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditDto {

    private String id;

    private String creditNumber;

    @NotNull(message = "creditType is required")
    private CreditType creditType;

    @NotBlank(message = "customerDocument is required")
    private String customerDocument;

    private String customerType;

    @NotNull(message = "creditLimit is required")
    @DecimalMin(value = "0.01", message = "creditLimit must be positive")
    private BigDecimal creditLimit;

    @DecimalMin(value = "0.0", message = "balance cannot be negative")
    private BigDecimal balance;
}
