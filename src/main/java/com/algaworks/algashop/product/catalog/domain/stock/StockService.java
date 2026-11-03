package com.algaworks.algashop.product.catalog.domain.stock;

import com.algaworks.algashop.product.catalog.domain.DomainEntityNotFoundException;
import com.algaworks.algashop.product.catalog.domain.DomainEventPublisher;
import com.algaworks.algashop.product.catalog.domain.DomainException;
import com.algaworks.algashop.product.catalog.domain.product.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// SERVICO DE DOMINIO, no sentido do DDD: comportamento de negocio que nao cabe dentro de
// um agregado so.
//
// Por que nao virou um metodo do Product: alterar estoque aqui NAO passa por carregar o
// agregado. O ajuste acontece direto no banco, numa operacao condicional atomica, porque
// e a unica forma de duas requisicoes simultaneas nao se atropelarem. Um product.withdraw()
// exigiria ler, decidir em Java e salvar - exatamente o que se quis evitar.
//
// Consequencia importante: como nao ha productRepository.save(), o mecanismo de eventos do
// AbstractAggregateRoot (que publica no save) nao serve aqui. Dai o DomainEventPublisher
// injetado - ver docs/01-arquitetura-design/eventos-e-listeners.md
//
// O @Service coloca uma anotacao do Spring dentro do dominio. Nao e ideal, mas tambem nao
// e novidade: Product e Category ja carregam anotacoes do Spring Data. A alternativa seria
// declarar o bean num @Configuration da infrastructure, mantendo esta classe limpa
@Service
@RequiredArgsConstructor
public class StockService {

    private final QuantityInStockAdjustment quantityInStockAdjustment;
    private final ProductRepository productRepository;

    public StockMovement restock(UUID productId, int quantity) {
        Objects.requireNonNull(productId);
        requirePositive(quantity);

        QuantityInStockAdjustment.Result result = quantityInStockAdjustment.increase(productId, quantity);

        return StockMovement.builder()
                .productId(productId)
                .movementQuantity(quantity)
                .previousQuantity(result.previousQuantity())
                .newQuantity(result.newQuantity())
                .type(StockMovement.MovementType.STOCK_IN)
                .build();
    }

    public StockMovement withdraw(UUID productId, int quantity, String orderId) {
        Objects.requireNonNull(productId);
        requirePositive(quantity);

        QuantityInStockAdjustment.Result result = quantityInStockAdjustment.decrease(productId, quantity);

        return StockMovement.builder()
                .productId(productId)
                .movementQuantity(quantity)
                .previousQuantity(result.previousQuantity())
                .newQuantity(result.newQuantity())
                .type(StockMovement.MovementType.STOCK_OUT)
                .orderId(orderId)
                .build();
    }

    // baixa em massa do estoque dos itens de um pedido, em 3 passos:
    // 1. agrupa  2. valida tudo antes  3. baixa um produto por vez
    public List<StockMovement> withdrawAll(String orderId, List<StockReservationItem> items) {
        // 1. agrupa por produto somando as quantidades: o mesmo produto em duas linhas
        // do pedido ({A:2}, {A:3}) vira uma baixa so ({A:5})
        Map<UUID, Integer> quantityByProduct = items.stream().collect(
                Collectors.groupingBy(
                        StockReservationItem::getProductId,
                        Collectors.summingInt(StockReservationItem::getQuantity)
                )
        );

        // busca todos os produtos numa consulta so (em vez de uma por item) e indexa
        // por id, para o passo 2 achar cada um direto no mapa
        Map<UUID, Product> productById = productRepository.findAllById(quantityByProduct.keySet())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 2. falha rapida: se qualquer produto nao existe ou nao tem saldo, recusa o pedido
        // inteiro antes de mexer no estoque de qualquer um. E so uma pre-checagem - outra
        // requisicao pode baixar o saldo depois dela; quem garante de verdade e o
        // decrease() atomico do passo 3
        quantityByProduct.forEach((productId, quantity) -> {
            Product product = productById.get(productId);

            if (product == null) {
                throw new ProductNotFoundException(productId);
            }

            if (!product.hasQuantity(quantity)) {
                throw new InsufficientStockException(productId, quantity, product.getQuantityInStock());
            }
        });

        // 3. baixa cada produto pelo withdraw() unitario e devolve um StockMovement por
        // produto. Quem chama abre a transacao (o reserveForOrder e @Transactional), e uma
        // falha no meio (saldo levado entre a checagem e a baixa) desfaz tambem as baixas
        // anteriores - o pedido baixa tudo ou nada
        return quantityByProduct.entrySet().stream()
                .map(entry -> withdraw(entry.getKey(), entry.getValue(), orderId))
                .toList();
    }

    // validado aqui apesar de o @Min(1) do ProductQuantityModel ja barrar na borda:
    // a borda protege a API, esta linha protege o dominio de qualquer outro chamador
    private void requirePositive(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }
}
