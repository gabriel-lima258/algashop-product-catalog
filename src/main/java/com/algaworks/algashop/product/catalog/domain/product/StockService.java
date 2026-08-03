package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.domain.DomainEntityNotFoundException;
import com.algaworks.algashop.product.catalog.domain.DomainEventPublisher;
import com.algaworks.algashop.product.catalog.domain.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.function.Supplier;

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
    private final DomainEventPublisher domainEventPublisher;

    public void restock(Product product, int quantity) {
        Objects.requireNonNull(product);
        requirePositive(quantity);

        QuantityInStockAdjustment.Result result = adjust(
                () -> quantityInStockAdjustment.increase(product.getId(), quantity),
                "Failed to restock quantity of product %s".formatted(product.getId()));

        // so quando VOLTA a ter estoque - reposicao sobre estoque que ja era positivo nao
        // e um fato interessante para ninguem
        if (result.isRestocked()) {
            domainEventPublisher.publish(
                    ProductRestockedEvent.builder()
                            .productId(product.getId())
                            .build()
            );
        }
    }

    public void withdraw(Product product, int quantity) {
        Objects.requireNonNull(product);
        requirePositive(quantity);

        QuantityInStockAdjustment.Result result = adjust(
                () -> quantityInStockAdjustment.decrease(product.getId(), quantity),
                "Failed to withdraw product %s".formatted(product.getId()));

        // so na transicao para zero, nao a cada saque com estoque baixo
        if (result.isOutOfStock()) {
            domainEventPublisher.publish(
                    ProductSoldOutEvent.builder()
                            .productId(product.getId())
                            .build()
            );
        }
    }

    // Erro de NEGOCIO passa direto; erro de INFRAESTRUTURA e embrulhado, com a causa
    // encadeada. A distincao decide a resposta HTTP e o que o cliente pode fazer:
    //
    // - InsufficientStockException / ProductNotFoundException -> 422 e 404. Sao respostas
    //   legitimas da API, com significado proprio, e engolir as duas num erro generico
    //   deixava o cliente sem saber se faltou saldo, se o produto sumiu ou se o banco caiu
    // - qualquer outra coisa (Mongo fora do ar, timeout) -> DomainException COM a causa.
    //   Passar o `e` adiante e o que preserva o stack trace do que realmente aconteceu
    private QuantityInStockAdjustment.Result adjust(
            Supplier<QuantityInStockAdjustment.Result> adjustment, String failureMessage) {
        try {
            return adjustment.get();
        } catch (DomainException | DomainEntityNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException(failureMessage, e);
        }
    }

    // validado aqui apesar de o @Min(1) do ProductQuantityModel ja barrar na borda:
    // a borda protege a API, esta linha protege o dominio de qualquer outro chamador
    private void requirePositive(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }
}
