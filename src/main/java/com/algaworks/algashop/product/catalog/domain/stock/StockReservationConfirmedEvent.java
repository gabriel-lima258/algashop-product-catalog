package com.algaworks.algashop.product.catalog.domain.stock;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@ToString
@Builder
public class StockReservationConfirmedEvent {

    private UUID reservationId;
    private String orderId;

    // valor padrão ao instanciar um builder
    @Builder.Default
    private OffsetDateTime confirmedAt = OffsetDateTime.now();
}