package com.algaworks.algashop.product.catalog.domain.stock;

import com.algaworks.algashop.product.catalog.domain.category.Category;
import com.algaworks.algashop.product.catalog.domain.product.Product;
import com.algaworks.algashop.product.catalog.domain.product.ProductNotFoundException;
import com.algaworks.algashop.product.catalog.domain.product.ProductRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Com a porta mockada da para exercitar o StockService sem Mongo nenhum. A regra que
// decide se sai ProductSoldOut/ProductRestocked NAO mora mais aqui: ela foi para o
// StockMovementRegisteredEvent (ver StockMovementRegisteredEventTest) e quem publica e o
// StockMovementEventListener (ver StockMovementEventListenerTest)
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    private static final String ORDER_ID = "order-1";

    @Mock
    private QuantityInStockAdjustment quantityInStockAdjustment;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private StockService stockService;

    // saldo insuficiente e produto ausente sao respostas legitimas da API e precisam
    // chegar intactas ao ApiExceptionHandler - 422 e 404, nao um erro generico
    @Test
    void shouldLetBusinessFailuresThrough() {
        UUID productId = aProduct().getId();
        when(quantityInStockAdjustment.decrease(eq(productId), anyInt()))
                .thenThrow(new InsufficientStockException(productId, 10, 3));

        assertThatExceptionOfType(InsufficientStockException.class)
                .isThrownBy(() -> stockService.withdraw(productId, 10, ORDER_ID));
    }

    @Test
    void shouldLetNotFoundThrough() {
        UUID productId = aProduct().getId();
        when(quantityInStockAdjustment.increase(eq(productId), anyInt()))
                .thenThrow(new ProductNotFoundException(productId));

        assertThatExceptionOfType(ProductNotFoundException.class)
                .isThrownBy(() -> stockService.restock(productId, 10));
    }

    // falha de infraestrutura sobe como veio: o servico nao embrulha nada, e quem decide
    // o que fazer com ela e a transacao em volta (rollback) e o handler da borda
    @Test
    void shouldLetInfrastructureFailuresThrough() {
        UUID productId = aProduct().getId();
        IllegalStateException mongoDown = new IllegalStateException("connection refused");
        when(quantityInStockAdjustment.decrease(eq(productId), anyInt())).thenThrow(mongoDown);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> stockService.withdraw(productId, 10, ORDER_ID))
                .isSameAs(mongoDown);
    }

    // quantidade invalida nem chega ao banco
    @Test
    void shouldRejectNonPositiveQuantityBeforeTouchingTheAdjustment() {
        UUID productId = aProduct().getId();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> stockService.withdraw(productId, 0, ORDER_ID));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> stockService.restock(productId, -1));

        verifyNoInteractions(quantityInStockAdjustment);
    }

    // O movimento e o extrato do ajuste: ele tem que repetir exatamente os numeros que
    // vieram do Result, senao o saldo e o historico contam versoes diferentes do mesmo fato
    @Test
    void shouldDescribeTheRestockAsAnIncomingMovement() {
        UUID productId = aProduct().getId();
        givenIncreaseResult(productId, 40, 50);

        StockMovement movement = stockService.restock(productId, 10);

        assertThat(movement.getProductId()).isEqualTo(productId);
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
        UUID productId = aProduct().getId();
        givenDecreaseResult(productId, 50, 40);

        StockMovement movement = stockService.withdraw(productId, 10, ORDER_ID);

        assertThat(movement.getProductId()).isEqualTo(productId);
        assertThat(movement.getType()).isEqualTo(StockMovement.MovementType.STOCK_OUT);
        assertThat(movement.getMovementQuantity()).isPositive().isEqualTo(10);
        assertThat(movement.getPreviousQuantity()).isEqualTo(50);
        assertThat(movement.getNewQuantity()).isEqualTo(40);
        assertThat(movement.getOrderId()).isEqualTo(ORDER_ID);
    }

    // dois movimentos com os mesmos numeros continuam sendo dois fatos distintos.
    // Com onlyExplicitlyIncluded e nenhum campo incluido, os dois seriam "iguais" e um
    // Set guardaria so um deles
    @Test
    void shouldTreatTwoIdenticalMovementsAsDistinctFacts() {
        UUID productId = aProduct().getId();
        givenDecreaseResult(productId, 50, 40);

        StockMovement first = stockService.withdraw(productId, 10, ORDER_ID);
        StockMovement second = stockService.withdraw(productId, 10, ORDER_ID);

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
