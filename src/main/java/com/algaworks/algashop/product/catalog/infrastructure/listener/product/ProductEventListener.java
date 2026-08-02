package com.algaworks.algashop.product.catalog.infrastructure.listener.product;

import com.algaworks.algashop.product.catalog.domain.product.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Consumidor dos eventos de dominio do Product. Hoje so registra em log - e o proposito e
// esse mesmo: tornar VISIVEL quando cada evento sai, que e a unica forma de perceber que
// eles so aparecem depois de um productRepository.save().
//
// Repare no contraste com o CategoryEventListener: aqui NAO ha @Async. Estes handlers
// rodam na mesma thread de quem salvou, logo apos o save. Uma excecao aqui sobe para o
// application service e o cliente ve o erro - o que e desejavel para um handler que
// participa da operacao, e indesejavel para uma propagacao em massa como a da categoria.
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
}
