package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.domain.category.Category;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

// A copia da categoria que vive DENTRO do documento de produto.
// Nao e @Document: nao tem colecao propria nem ciclo de vida proprio - e um value object,
// gravado embutido como { category: { _id, name, enabled } }.
//
// Nao e a Category inteira de proposito: copia-se so o que a listagem le. Cada campo a
// mais aqui e um campo a mais para manter sincronizado a cada rename, e um documento de
// produto maior em disco e em memoria. A pergunta para incluir um campo novo nao e "o
// produto tem acesso a esse dado?", e "vale reescrever N produtos quando ele mudar?".
//
// ATENCAO: o campo id vira _id no documento. Toda propriedade chamada id de um objeto
// embutido recebe esse tratamento do Spring Data - e por isso que o indice nasce como
// category._id enquanto a anotacao no Product diz category.id.
//
// Construtor sem argumentos protegido: o Spring Data materializa por reflexao, mas de
// fora so da para criar passando por of(), que garante que a copia veio de uma Category
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCategory {
    private UUID id;
    private String name;
    private Boolean enabled;

    public static ProductCategory of(Category category) {
        return new ProductCategory(category.getId(), category.getName(), category.getEnabled());
    }
}
