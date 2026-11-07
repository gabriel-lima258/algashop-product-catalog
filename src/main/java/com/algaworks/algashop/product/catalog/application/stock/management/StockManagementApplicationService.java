package com.algaworks.algashop.product.catalog.application.stock.management;

import com.algaworks.algashop.product.catalog.domain.product.ProductNotFoundException;
import com.algaworks.algashop.product.catalog.domain.stock.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockManagementApplicationService {

    // service
    private final StockService stockService;

    // repositories
    private final StockReservationRepository stockReservationRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional
    public UUID reserveForOrder(StockReservationInput input) {
        Objects.requireNonNull(input);

        String orderId = input.getOrderId();
        assertReservationDoesNotExists(orderId);

        List<StockReservationItem> items = toItems(input);

        List<StockMovement> movements;
        StockReservation reservation;

        try {
            // registra movimento e reservas
            movements = stockService.withdrawAll(orderId, items);
            reservation = StockReservation.confirmed(orderId, items);
        } catch (InsufficientStockException | ProductNotFoundException e) {
            movements = List.of();
            reservation = StockReservation.rejected(orderId, items);
        }

        // salva a movimentação e reserva de produto
        if (!movements.isEmpty()) {
            stockMovementRepository.saveAll(movements);
        }
        stockReservationRepository.save(reservation);

        return reservation.getId();
    }

    private List<StockReservationItem> toItems(StockReservationInput input) {
        return input.getItems().stream().map(item -> StockReservationItem.builder()
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .build()).toList();
    }

    private void assertReservationDoesNotExists(String orderId) {
        if (stockReservationRepository.existsByOrderId(orderId)) {
            throw new DuplicateStockReservationException(orderId);
        }
    }

}
