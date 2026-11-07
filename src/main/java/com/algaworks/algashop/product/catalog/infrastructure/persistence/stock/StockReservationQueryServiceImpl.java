package com.algaworks.algashop.product.catalog.infrastructure.persistence.stock;

import com.algaworks.algashop.product.catalog.application.stock.query.StockReservationOutput;
import com.algaworks.algashop.product.catalog.application.stock.query.StockReservationQueryService;
import com.algaworks.algashop.product.catalog.domain.stock.StockReservation;
import com.algaworks.algashop.product.catalog.domain.stock.StockReservationNotFoundException;
import com.algaworks.algashop.product.catalog.domain.stock.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockReservationQueryServiceImpl implements StockReservationQueryService {

    private final StockReservationRepository stockReservationRepository;

    @Override
    public StockReservationOutput findById(UUID reservationId) {
        StockReservation stockReservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new StockReservationNotFoundException(reservationId));
        return StockReservationOutput.builder()
                .id(stockReservation.getId())
                .orderId(stockReservation.getOrderId())
                .confirmed(stockReservation.isConfirmed())
                .createdAt(stockReservation.getCreatedAt())
                .build();
    }
}
