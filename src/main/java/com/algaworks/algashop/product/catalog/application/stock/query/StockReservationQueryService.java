package com.algaworks.algashop.product.catalog.application.stock.query;

import java.util.UUID;

// Lado de leitura da reserva: o listener do comando le por aqui o que vai na resposta
public interface StockReservationQueryService {
    StockReservationOutput findById(UUID reservationId);
}
