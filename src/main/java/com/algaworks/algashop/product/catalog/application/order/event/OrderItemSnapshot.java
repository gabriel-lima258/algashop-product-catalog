package com.algaworks.algashop.product.catalog.application.order.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemSnapshot(
        @NotBlank String id,
        @NotNull UUID productId,
        @NotBlank String productName,
        @NotNull @Positive BigDecimal price,
        @NotNull @Positive Integer quantity,
        @NotNull @Positive BigDecimal totalAmount
) {
}