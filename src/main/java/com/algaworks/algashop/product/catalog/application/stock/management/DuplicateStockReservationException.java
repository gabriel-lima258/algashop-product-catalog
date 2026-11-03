package com.algaworks.algashop.product.catalog.application.stock.management;

import com.algaworks.algashop.product.catalog.domain.DomainException;

public class DuplicateStockReservationException extends DomainException {

    public DuplicateStockReservationException(String orderId) {
        super("Reservation already exists for order " + orderId);
    }

    public DuplicateStockReservationException(String orderId, Throwable cause) {
        super("Reservation already exists for order " + orderId, cause);
    }

}