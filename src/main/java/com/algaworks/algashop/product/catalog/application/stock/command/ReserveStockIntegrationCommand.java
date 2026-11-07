package com.algaworks.algashop.product.catalog.application.stock.command;

import com.algaworks.algashop.product.catalog.application.InboundIntegrationCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReserveStockIntegrationCommand
        implements InboundIntegrationCommand {

    @NotBlank
    private String orderId;

    @Builder.Default
    @NotNull
    @Size(min = 1)
    @Valid
    private List<Item> items = new ArrayList<>();

    public record Item(@NotNull UUID productId, @NotNull @Positive Integer quantity) {
    }
}