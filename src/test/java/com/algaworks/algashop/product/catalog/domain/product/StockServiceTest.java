package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.domain.DomainEventPublisher;
import com.algaworks.algashop.product.catalog.domain.DomainException;
import com.algaworks.algashop.product.catalog.domain.category.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Com a porta mockada da para exercitar a regra que decide se o evento sai, sem Mongo
// nenhum. O que se testa aqui e a TRANSICAO: a mesma quantidade final produz evento ou
// nao dependendo de onde o estoque estava antes.
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private QuantityInStockAdjustment quantityInStockAdjustment;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private StockService stockService;

    @Test
    void shouldPublishRestockedWhenStockLeavesZero() {
        Product product = aProduct();
        givenIncreaseResult(product.getId(), 0, 10);

        stockService.restock(product, 10);

        verify(domainEventPublisher).publish(any(ProductRestockedEvent.class));
    }

    @Test
    void shouldNotPublishRestockedWhenStockWasAlreadyPositive() {
        Product product = aProduct();
        // 40 -> 50 tambem termina positivo, mas nao e reposicao: nao houve transicao
        givenIncreaseResult(product.getId(), 40, 50);

        stockService.restock(product, 10);

        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void shouldPublishSoldOutWhenStockReachesZero() {
        Product product = aProduct();
        givenDecreaseResult(product.getId(), 10, 0);

        stockService.withdraw(product, 10);

        verify(domainEventPublisher).publish(any(ProductSoldOutEvent.class));
    }

    @Test
    void shouldNotPublishSoldOutWhenStockRemains() {
        Product product = aProduct();
        givenDecreaseResult(product.getId(), 50, 40);

        stockService.withdraw(product, 10);

        verify(domainEventPublisher, never()).publish(any());
    }

    // saldo insuficiente e produto ausente sao respostas legitimas da API e precisam
    // chegar intactas ao ApiExceptionHandler - 422 e 404, nao um erro generico
    @Test
    void shouldLetBusinessFailuresThrough() {
        Product product = aProduct();
        when(quantityInStockAdjustment.decrease(eq(product.getId()), anyInt()))
                .thenThrow(new InsufficientStockException(product.getId(), 10, 3));

        assertThatExceptionOfType(InsufficientStockException.class)
                .isThrownBy(() -> stockService.withdraw(product, 10));

        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void shouldLetNotFoundThrough() {
        Product product = aProduct();
        when(quantityInStockAdjustment.increase(eq(product.getId()), anyInt()))
                .thenThrow(new ProductNotFoundException(product.getId()));

        assertThatExceptionOfType(ProductNotFoundException.class)
                .isThrownBy(() -> stockService.restock(product, 10));
    }

    // falha de infraestrutura vira DomainException, mas COM a causa encadeada -
    // era isso que o catch(Exception) sem cause jogava fora
    @Test
    void shouldWrapInfrastructureFailuresKeepingTheCause() {
        Product product = aProduct();
        IllegalStateException mongoDown = new IllegalStateException("connection refused");
        when(quantityInStockAdjustment.decrease(eq(product.getId()), anyInt())).thenThrow(mongoDown);

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> stockService.withdraw(product, 10))
                .withCause(mongoDown);
    }

    // quantidade invalida nem chega ao banco
    @Test
    void shouldRejectNonPositiveQuantityBeforeTouchingTheAdjustment() {
        Product product = aProduct();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> stockService.withdraw(product, 0));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> stockService.restock(product, -1));

        verifyNoInteractions(quantityInStockAdjustment);
    }

    // O movimento e o extrato do ajuste: ele tem que repetir exatamente os numeros que
    // vieram do Result, senao o saldo e o historico contam versoes diferentes do mesmo fato
    @Test
    void shouldDescribeTheRestockAsAnIncomingMovement() {
        Product product = aProduct();
        givenIncreaseResult(product.getId(), 40, 50);

        StockMovement movement = stockService.restock(product, 10);

        assertThat(movement.getProductId()).isEqualTo(product.getId());
        assertThat(movement.getType()).isEqualTo(StockMovement.MovementType.STOCK_IN);
        assertThat(movement.getMovementQuantity()).isEqualTo(10);
        assertThat(movement.getPreviousQuantity()).isEqualTo(40);
        assertThat(movement.getNewQuantity()).isEqualTo(50);
        assertThat(movement.getOccurredAt()).isNotNull();
    }

    // o sinal mora no type, nunca na quantidade: uma saida de 10 grava 10, e nao -10.
    // Se isso inverter, toda soma de "quanto saiu" passa a mentir em silencio
    @Test
    void shouldDescribeTheWithdrawAsAnOutgoingMovementWithPositiveQuantity() {
        Product product = aProduct();
        givenDecreaseResult(product.getId(), 50, 40);

        StockMovement movement = stockService.withdraw(product, 10);

        assertThat(movement.getProductId()).isEqualTo(product.getId());
        assertThat(movement.getType()).isEqualTo(StockMovement.MovementType.STOCK_OUT);
        assertThat(movement.getMovementQuantity()).isPositive().isEqualTo(10);
        assertThat(movement.getPreviousQuantity()).isEqualTo(50);
        assertThat(movement.getNewQuantity()).isEqualTo(40);
    }

    // dois movimentos com os mesmos numeros continuam sendo dois fatos distintos.
    // Com onlyExplicitlyIncluded e nenhum campo incluido, os dois seriam "iguais" e um
    // Set guardaria so um deles
    @Test
    void shouldTreatTwoIdenticalMovementsAsDistinctFacts() {
        Product product = aProduct();
        when(quantityInStockAdjustment.decrease(eq(product.getId()), anyInt()))
                .thenReturn(new QuantityInStockAdjustment.Result(product.getId(), 50, 40));

        StockMovement first = stockService.withdraw(product, 10);
        StockMovement second = stockService.withdraw(product, 10);

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first).isNotEqualTo(second);
        assertThat(Set.of(first, second)).hasSize(2);
    }

    private void givenIncreaseResult(UUID productId, int previous, int current) {
        when(quantityInStockAdjustment.increase(eq(productId), anyInt()))
                .thenReturn(new QuantityInStockAdjustment.Result(productId, previous, current));
    }

    private void givenDecreaseResult(UUID productId, int previous, int current) {
        when(quantityInStockAdjustment.decrease(eq(productId), anyInt()))
                .thenReturn(new QuantityInStockAdjustment.Result(productId, previous, current));
    }

    private Product aProduct() {
        return Product.builder()
                .name("HyperNova Pro X11")
                .brand("QuantumTech")
                .enabled(true)
                .regularPrice(new BigDecimal("3000"))
                .salePrice(new BigDecimal("2789"))
                .category(new Category("Laptops", true))
                .build();
    }
}
