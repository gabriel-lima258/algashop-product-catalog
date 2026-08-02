package com.algaworks.algashop.product.catalog.infrastructure.persistence.category;

import com.algaworks.algashop.product.catalog.application.category.event.CategoryUpdatedEvent;
import com.algaworks.algashop.product.catalog.domain.product.Product;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

// Onde a copia desnormalizada e realinhada com a origem. Uma escrita so, no servidor:
// updateMulti pede ao Mongo que percorra e altere os documentos casados, em vez de
// carregar N produtos para a JVM, chamar setCategory em cada um e salvar de volta.
//
// Repare que o Product NAO passa pelo repositorio aqui - e uma escrita direta por
// MongoOperations. Isso e intencional (nao ha regra de negocio a aplicar), mas tem uma
// consequencia facil de esquecer: nenhum evento de dominio do Product e publicado, e o
// @Version nao e incrementado, entao a alteracao escapa do lock otimista
@Component
@AllArgsConstructor
public class ProductCategoryUpdater {

    private final MongoOperations mongoOperations;

    public void copyCategoryDataToProducts(CategoryUpdatedEvent categoryUpdatedEvent) {
        // "category._id" e o nome do campo COMO ESTA GRAVADO no documento. Escrever
        // "category.id" tambem funcionaria - o QueryMapper resolveria o path pelo
        // mapping context, como faz na def do @CompoundIndex - mas o nome cru deixa
        // visivel o que de fato vai no filtro do update
        Query query = new Query(
                Criteria.where("category._id").is(categoryUpdatedEvent.getCategoryId())
        );

        // so os campos copiados. o id nao entra: e ele o criterio de busca, e mudar a
        // identidade da categoria de um produto seria recategorizar, nao sincronizar
        Update update = new Update()
                .set("category.name", categoryUpdatedEvent.getName())
                .set("category.enabled", categoryUpdatedEvent.getEnabled());

        // ATENCAO ao custo: este filtro NAO casa o partialFilter { enabled: true } dos
        // indices compostos, porque nao manda enabled na consulta - e nem poderia, ja que
        // produto inativo tambem precisa ter a copia atualizada. Resultado: a propagacao e
        // uma varredura da colecao, e ainda escreve em campo indexado, mexendo nos dois
        // indices compostos. Registrado como pendencia em
        // docs/02-persistencia/desnormalizacao-mongo.md
        mongoOperations.updateMulti(query, update, Product.class);
    }
}
