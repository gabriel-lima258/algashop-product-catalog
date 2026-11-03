package com.algaworks.algashop.product.catalog.application.stock.management;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockReservationItemInput {

    @NotNull
    private UUID productId;

    @NotNull
    @Positive
    private Integer quantity;
}
