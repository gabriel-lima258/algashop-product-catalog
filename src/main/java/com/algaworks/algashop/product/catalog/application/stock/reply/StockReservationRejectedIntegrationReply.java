package com.algaworks.algashop.product.catalog.application.stock.reply;

import com.algaworks.algashop.product.catalog.application.OutboundIntegrationReply;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockReservationRejectedIntegrationReply
        implements OutboundIntegrationReply {

    @NotNull
    private UUID reservationId;

    @NotBlank
    private String orderId;

    @NotNull
    private OffsetDateTime rejectedAt;

    @Override
    public String getAggregateId() {
        return orderId;
    }
}
