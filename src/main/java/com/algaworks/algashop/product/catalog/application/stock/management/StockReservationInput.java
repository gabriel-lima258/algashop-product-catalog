package com.algaworks.algashop.product.catalog.application.stock.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockReservationInput {

    @NotBlank
    private String orderId;

    @NotNull
    @Size(min = 1)
    @Valid
    private List<StockReservationItemInput> items;
}