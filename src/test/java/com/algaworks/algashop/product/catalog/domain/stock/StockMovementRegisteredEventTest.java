package com.algaworks.algashop.product.catalog.domain.stock;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// O que se testa aqui e a TRANSICAO: a mesma quantidade final produz evento ou nao
// dependendo de onde o estoque estava antes
class StockMovementRegisteredEventTest {

    @Test
    void shouldReportSoldOutWhenStockReachesZero() {
        assertThat(movement(10, 0).causedSoldOut()).isTrue();
    }

    @Test
    void shouldNotReportSoldOutWhenStockRemains() {
        assertThat(movement(50, 40).causedSoldOut()).isFalse();
    }

    // ja estava zerado: nao houve transicao, entao nao e "esgotou agora"
    @Test
    void shouldNotReportSoldOutWhenStockWasAlreadyZero() {
        assertThat(movement(0, 0).causedSoldOut()).isFalse();
    }

    @Test
    void shouldReportRestockWhenStockLeavesZero() {
        assertThat(movement(0, 10).causedRestock()).isTrue();
    }

    // 40 -> 50 tambem termina positivo, mas nao e reposicao: nao houve transicao
    @Test
    void shouldNotReportRestockWhenStockWasAlreadyPositive() {
        assertThat(movement(40, 50).causedRestock()).isFalse();
    }

    private StockMovementRegisteredEvent movement(int previous, int current) {
        return StockMovementRegisteredEvent.builder()
                .productId(UUID.randomUUID())
                .previousQuantity(previous)
                .newQuantity(current)
                .build();
    }
}
