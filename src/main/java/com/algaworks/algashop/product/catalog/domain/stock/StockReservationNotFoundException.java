package com.algaworks.algashop.product.catalog.domain.stock;

import com.algaworks.algashop.product.catalog.domain.DomainEntityNotFoundException;

import java.util.UUID;

public class StockReservationNotFoundException
        extends DomainEntityNotFoundException {
    public StockReservationNotFoundException(UUID reservationId) {
        super(String.format("Stock reservation with id %s was not found", reservationId));
    }
}
