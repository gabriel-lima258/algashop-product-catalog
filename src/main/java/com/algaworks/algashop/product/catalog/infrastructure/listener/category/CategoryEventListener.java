package com.algaworks.algashop.product.catalog.infrastructure.listener.category;

import com.algaworks.algashop.product.catalog.application.category.event.CategoryUpdatedEvent;
import com.algaworks.algashop.product.catalog.infrastructure.persistence.category.ProductCategoryUpdater;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// O consumidor que paga a conta da desnormalizacao: a categoria mudou, e a copia dela
// dentro de cada produto precisa acompanhar.
//
// @Async e uma TROCA deliberada, nao um detalhe. Quem chama PUT /categories/{id} recebe a
// resposta assim que a categoria e gravada, sem esperar a reescrita dos produtos - a API
// nao fica refem do tamanho da categoria. Em troca, existe uma janela em que a categoria
// ja tem o nome novo e a listagem de produtos ainda mostra o antigo: consistencia
// eventual, exatamente no sentido do "E" de BASE.
//
// ATENCAO ao que NAO existe aqui: nao ha retentativa, nao ha fila persistente e nao ha
// ordem garantida entre dois updates seguidos da mesma categoria. Se o updateMulti
// estourar, a excecao morre na thread do executor e os produtos ficam com o dado velho
// PARA SEMPRE - sem ninguem para notar. Com broker de verdade isso viraria retry e dead
// letter; ver docs/01-arquitetura-design/eventos-e-listeners.md
//
// @EventListener e nao @TransactionalEventListener porque nao ha transacao envolvida: o
// service ja gravou a categoria antes de publicar
@Component
@Slf4j
@AllArgsConstructor
public class CategoryEventListener {

    private final ProductCategoryUpdater productCategoryUpdater;

    @EventListener
    @Async
    public void handle(CategoryUpdatedEvent categoryUpdatedEvent) {
        productCategoryUpdater.copyCategoryDataToProducts(categoryUpdatedEvent);
        log.info("Category updated received: {}", categoryUpdatedEvent.getCategoryId());
    }
}
