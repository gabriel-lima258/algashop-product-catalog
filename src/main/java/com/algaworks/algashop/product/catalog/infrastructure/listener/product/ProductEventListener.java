package com.algaworks.algashop.product.catalog.infrastructure.listener.product;

import com.algaworks.algashop.product.catalog.domain.product.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// Consumidor dos eventos de dominio do Product. Hoje so registra em log - e o proposito e
// esse mesmo: tornar VISIVEL quando cada evento sai, que e a unica forma de perceber que
// eles so aparecem depois de um productRepository.save().
//
// Quase todos os handlers aqui sao SINCRONOS: rodam na mesma thread de quem salvou, logo
// apos o save. Uma excecao sobe para o application service e o cliente ve o erro - o que e
// desejavel para um handler que participa da operacao, e indesejavel para uma propagacao
// em massa como a da categoria (por isso o CategoryEventListener e @Async).
//
// A excecao e o ProductPriceChangedEvent, que ganhou @Async. Vale entender o que isso muda
// perto de uma transacao, porque a diferenca e maior do que parece:
//
//   sincrono -> roda DENTRO da transacao de quem publicou. Se estourar, o rollback leva a
//               escrita junto. E o caso de restock/withdraw: o evento nao e um "depois",
//               e parte do mesmo commit
//   @Async   -> roda em OUTRA thread, portanto fora da sessao transacional. Se estourar,
//               vira log e a escrita segue commitada; e se ele ler o banco, le o estado
//               de antes do commit, porque a transacao ainda nao terminou
//
// Hoje o @Async aqui e inofensivo: changePrice sai pelo update(), que nao e @Transactional.
// Mas anotar qualquer handler de estoque com @Async o tiraria do rollback silenciosamente -
// nada quebraria, nada avisaria, e a garantia simplesmente deixaria de existir.
// Ver transacoes-mongo.md.
//
// A assinatura ja diz o tipo, entao o @EventListener(X.class) e redundante - fica por
// legibilidade, deixando o filtro explicito na anotacao
@Component
@Slf4j
public class ProductEventListener {

    @EventListener(ProductAddedEvent.class)
    public void handle(ProductAddedEvent event) {
        log.info("ProductAddedEvent: {}", event);
    }

    @EventListener(ProductPriceChangedEvent.class)
    @Async
    public void handle(ProductPriceChangedEvent event) {
        log.info("ProductPriceChangedEvent: {}", event);
    }

    @EventListener(ProductPlacedOnSaleEvent.class)
    public void handle(ProductPlacedOnSaleEvent event) {
        log.info("ProductPlacedOnSaleEvent: {}", event);
    }

    @EventListener(ProductListedEvent.class)
    public void handle(ProductListedEvent event) {
        log.info("ProductListedEvent: {}", event);
    }

    @EventListener(ProductDelistedEvent.class)
    public void handle(ProductDelistedEvent event) {
        log.info("ProductDelistedEvent: {}", event);
    }

    @EventListener(ProductRestockedEvent.class)
    public void handle(ProductRestockedEvent event) {
        log.info("ProductRestockedEvent: {}", event);
    }

    @EventListener(ProductSoldOutEvent.class)
    public void handle(ProductSoldOutEvent event) {
        log.info("ProductSoldOutEvent: {}", event);
    }
}
