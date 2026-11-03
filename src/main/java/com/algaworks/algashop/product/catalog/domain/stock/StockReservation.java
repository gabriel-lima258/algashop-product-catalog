package com.algaworks.algashop.product.catalog.domain.stock;

import com.algaworks.algashop.product.catalog.domain.util.IdGenerator;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Document(collection = "stock_reservations")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class StockReservation {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @Indexed(name = "uidx_stock_reservation_by_order", unique = true)
    private String orderId;

    private Status status;

    private OffsetDateTime createdAt;

    private List<StockReservationItem> items;

    private StockReservation(String orderId, Status status, List<StockReservationItem> items) {
        this.id = IdGenerator.generateTimeBasedUUID();
        this.orderId = Objects.requireNonNull(orderId);
        this.status = Objects.requireNonNull(status);
        this.createdAt = OffsetDateTime.now();

        if (items.isEmpty()) {
            throw new IllegalArgumentException("Item list cannot be empty");
        }

        this.items = List.copyOf(items);
    }

    // instancia a factory como rejeitada ou confirmada
    public static StockReservation confirmed(String orderId, List<StockReservationItem> items) {
        return new StockReservation(orderId, Status.CONFIRMED, items);
    }

    public static StockReservation rejected(String orderId, List<StockReservationItem> items) {
        return new StockReservation(orderId, Status.REJECT, items);
    }

    public List<StockReservationItem> getItems() {
        return Collections.unmodifiableList(this.items);
    }

    public boolean isConfirmed() {
        return Status.CONFIRMED.equals(this.getStatus());
    }

    public boolean isRejected() {
        return Status.REJECT.equals(this.getStatus());
    }

    public enum Status {
        CONFIRMED,
        REJECT
    }
}
