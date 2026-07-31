package com.algaworks.algashop.product.catalog.infrastructure.persistence.product;

import com.algaworks.algashop.product.catalog.application.product.query.ProductFilter;
import com.algaworks.algashop.product.catalog.application.util.PageModel;
import com.algaworks.algashop.product.catalog.application.product.query.ProductDetailOutput;
import com.algaworks.algashop.product.catalog.application.product.query.ProductQueryService;
import com.algaworks.algashop.product.catalog.application.product.query.ProductSummaryOutput;
import com.algaworks.algashop.product.catalog.application.util.Mapper;
import com.algaworks.algashop.product.catalog.domain.product.Product;
import com.algaworks.algashop.product.catalog.domain.product.ProductNotFoundException;
import com.algaworks.algashop.product.catalog.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.AggregationExpressionCriteria;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductQueryServiceImpl implements ProductQueryService {

    private final ProductRepository productRepository;
    private final Mapper mapper;

    // criteria query
    private final MongoOperations mongoOperations;

    @Override
    public ProductDetailOutput findById(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return mapper.convert(product, ProductDetailOutput.class);
    }

    @Override
    public PageModel<ProductSummaryOutput> filter(ProductFilter filter) {
        // 1. monta os criterias de filtro
        Query query = queryWith(filter);

        // 2. conta o total ANTES de paginar - mesma query, sem skip/limit
        long totalItems = mongoOperations.count(query, Product.class);

        // 3. aplica ordenacao + skip/limit sobre a mesma query filtrada
        Sort sort = sortWith(filter);
        PageRequest pageRequest = PageRequest.of(filter.getPage(), filter.getSize(), sort);
        Query pagedQuery = query.with(pageRequest);

        List<Product> products;
        int totalPages = 0;

        if (totalItems > 0) {
            products = mongoOperations.find(pagedQuery, Product.class);
            totalPages = (int) Math.ceil((double) totalItems / pageRequest.getPageSize());
        } else {
            products = new ArrayList<>();
        }

        List<ProductSummaryOutput> productsOutput =
                products.stream()
                        .map(p -> mapper.convert(p, ProductSummaryOutput.class))
                        .collect(Collectors.toList());

        return PageModel.<ProductSummaryOutput>builder()
                .content(productsOutput)
                .number(pageRequest.getPageNumber())
                .size(pageRequest.getPageSize())
                .totalElements(totalItems)
                .totalPages(totalPages)
                .build();
    }

    // ordenacao: o que o cliente pediu ou o default do ProductFilter (addedAt ASC)
    private Sort sortWith(ProductFilter filter) {
        // busca textual ignora o sort pedido e ordena por relevancia. "score" e o campo
        // @TextScore do Product, e por isso o Spring Data traduz para $meta: "textScore"
        if (StringUtils.isNotBlank(filter.getTerm())) {
            return Sort.by("score");
        }
        return Sort.by(filter.getSortDirectionOrDefault(),
                filter.getSortByPropertyOrDefault().getPropertyName());
    }

    // filtro dinamico: cada campo preenchido vira um criteria a mais na query.
    // criterias somados se combinam com AND; campo null e ignorado (nao filtra).
    private Query queryWith(ProductFilter filter) {
        Query query = new Query();

        // enabled -> { "enabled": true|false }
        // null traz ativos e inativos; true so os ativos; false so os inativos
        if (filter.getEnabled() != null) {
            query.addCriteria(Criteria.where("enabled").is(filter.getEnabled()));
        }

        // addedAt -> intervalo de data. Os dois extremos sao opcionais e independentes:
        // com ambos vira { "addedAt": { $gte: X, $lte: Y } }; com um so, fica aberto do outro lado.
        // os dois criterias precisam ser encadeados no MESMO where("addedAt") -
        // dois addCriteria no mesmo campo o Mongo rejeita como chave duplicada
        if (filter.getAddedAtFrom() != null && filter.getAddedAtTo() != null) {
            query.addCriteria(Criteria.where("addedAt")
                    .gte(filter.getAddedAtFrom())
                    .lte(filter.getAddedAtTo())
            );
        } else {
            if (filter.getAddedAtFrom() != null) {
                query.addCriteria(Criteria.where("addedAt").gte(filter.getAddedAtFrom()));
            } else if (filter.getAddedAtTo() != null) {
                query.addCriteria(Criteria.where("addedAt").lte(filter.getAddedAtTo()));
            }
        }

        // faixa de preco - mesma estrutura do addedAt.
        // filtra pelo salePrice (o que o cliente paga), nao pelo regularPrice
        if (filter.getPriceFrom() != null && filter.getPriceTo() != null) {
            query.addCriteria(Criteria.where("salePrice")
                    .gte(filter.getPriceFrom())
                    .lte(filter.getPriceTo())
            );
        } else {
            if (filter.getPriceFrom() != null) {
                query.addCriteria(Criteria.where("salePrice").gte(filter.getPriceFrom()));
            } else if (filter.getPriceTo() != null) {
                query.addCriteria(Criteria.where("salePrice").lte(filter.getPriceTo()));
            }
        }

        // hasDiscount -> compara DOIS CAMPOS do mesmo documento (salePrice x regularPrice).
        // Criteria comum so compara campo com valor literal, por isso aqui vai
        // AggregationExpressionCriteria, que gera { $expr: { $lt: ["$salePrice", "$regularPrice"] } }.
        // custo: $expr nao usa indice - varre a colecao inteira
        if (filter.getHasDiscount() != null) {
            if (filter.getHasDiscount()) {
                query.addCriteria(AggregationExpressionCriteria.whereExpr(
                        ComparisonOperators.valueOf("$salePrice")
                                .lessThan("$regularPrice")
                ));
            } else {
                query.addCriteria(AggregationExpressionCriteria.whereExpr(
                        ComparisonOperators.valueOf("$salePrice")
                                .equalTo("$regularPrice")
                ));
            }
        }

        // inStock -> tem ou nao estoque. campo derivado nao existe no documento,
        // entao a regra do isInStock() e reescrita aqui em cima de quantityInStock
        if (filter.getInStock() != null) {
            if (filter.getInStock()) {
                query.addCriteria(Criteria.where("quantityInStock").gt(0));
            } else {
                query.addCriteria(Criteria.where("quantityInStock").is(0));
            }
        }

        // categoriesId -> { "categoryId": { $in: [...] } }.
        // o campo procurado e o categoryId gravado pelo @DocumentReference do Product,
        // nao a categoria inteira - por isso da pra filtrar sem carregar nenhuma Category
        if (filter.getCategoriesId() != null && filter.getCategoriesId().length > 0) {
            query.addCriteria(Criteria.where("categoryId").in(
                    (Object[]) filter.getCategoriesId()
            ));
        }

        // term -> { $text: { $search: "..." } }, servido pelo indice de texto (@TextIndexed
        // em name e description). Substituiu o $or de tres regex, que varria a colecao inteira.
        // o que se ganhou: usa indice, faz stemming e nao ha risco de ReDoS.
        // o que se perdeu: casa palavra inteira ("note" nao acha "notebook", entao busca
        // enquanto o usuario digita deixou de funcionar) e brand ficou de fora da busca
        if (StringUtils.isNotBlank(filter.getTerm())) {
            query.addCriteria(TextCriteria.forDefaultLanguage().matching(filter.getTerm()));
        }

        return query;
    }
}
