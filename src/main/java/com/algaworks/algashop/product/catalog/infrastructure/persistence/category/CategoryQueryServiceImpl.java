package com.algaworks.algashop.product.catalog.infrastructure.persistence.category;

import com.algaworks.algashop.product.catalog.application.category.query.CategoryFilter;
import com.algaworks.algashop.product.catalog.application.util.PageModel;
import com.algaworks.algashop.product.catalog.application.category.query.CategoryDetailOutput;
import com.algaworks.algashop.product.catalog.application.category.query.CategoryQueryService;
import com.algaworks.algashop.product.catalog.application.util.Mapper;
import com.algaworks.algashop.product.catalog.domain.category.Category;
import com.algaworks.algashop.product.catalog.domain.category.CategoryNotFoundException;
import com.algaworks.algashop.product.catalog.domain.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryQueryServiceImpl implements CategoryQueryService {

    private final CategoryRepository categoryRepository;
    private final Mapper mapper;
    // criteria query
    private final MongoOperations mongoOperations;

    @Override
    public CategoryDetailOutput findById(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        return mapper.convert(category, CategoryDetailOutput.class);
    }

    // mesmo padrao do ProductQueryServiceImpl, passo a passo - a segunda vez que a
    // hierarquia PageFilter/SortablePageFilter e usada, e onde ela comeca a se pagar
    @Override
    public PageModel<CategoryDetailOutput> filter(CategoryFilter filter) {
        // 1. monta os criterias de filtro
        Query query = queryWith(filter);

        // 2. conta o total ANTES de paginar - mesma query, sem skip/limit
        long totalItems = mongoOperations.count(query, Category.class);

        // 3. aplica ordenacao + skip/limit sobre a mesma query filtrada
        Sort sort = sortWith(filter);
        PageRequest pageRequest = PageRequest.of(filter.getPage(), filter.getSize(), sort);
        Query pagedQuery = query.with(pageRequest);

        List<Category> categories;
        int totalPages = 0;

        if (totalItems > 0) {
            categories = mongoOperations.find(pagedQuery, Category.class);
            totalPages = (int) Math.ceil((double) totalItems / pageRequest.getPageSize());
        } else {
            categories = new ArrayList<>();
        }

        List<CategoryDetailOutput> categoriesOutput =
                categories.stream()
                        .map(p -> mapper.convert(p, CategoryDetailOutput.class))
                        .collect(Collectors.toList());

        return PageModel.<CategoryDetailOutput>builder()
                .content(categoriesOutput)
                .number(pageRequest.getPageNumber())
                .size(pageRequest.getPageSize())
                .totalElements(totalItems)
                .totalPages(totalPages)
                .build();
    }

    // consulta da ultima categoria modificada
    @Override
    public OffsetDateTime lastModified() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group().max("updatedAt").as("lastModified")
        );
        AggregationResults<Document> result = mongoOperations.aggregate(aggregation,
                "categories", Document.class);

        Document document = result.getUniqueMappedResult();

        if (document == null) {
            return OffsetDateTime.now();
        }

        return document.getDate("lastModified").toInstant().atOffset(ZoneOffset.UTC);
    }

    // ordenacao: o que o cliente pediu ou o default do CategoryFilter (name ASC)
    private Sort sortWith(CategoryFilter filter) {
        return Sort.by(filter.getSortDirectionOrDefault(),
                filter.getSortByPropertyOrDefault().getPropertyName());
    }

    // filtro dinamico: cada campo preenchido vira um criteria a mais na query.
    // criterias somados se combinam com AND; campo null e ignorado (nao filtra).
    private Query queryWith(CategoryFilter filter) {
        Query query = new Query();

        // enabled -> { "enabled": true|false }
        // null traz ativos e inativos; true so os ativos; false so os inativos
        if (filter.getEnabled() != null) {
            query.addCriteria(Criteria.where("enabled").is(filter.getEnabled()));
        }

        // name -> { "name": { $regex: "x", $options: "i" } }, casa em qualquer parte do texto.
        // o "i" e a flag de ignorar maiusculas/minusculas.
        // ATENCAO: regex sem ancora no inicio (^) nao usa indice, e Category nao declara
        // nenhum indice - ou seja, e varredura de colecao, o oposto do que o Product faz.
        // aqui nao incomoda (sao poucas categorias); em colecao grande incomodaria.
        // o termo tambem entra cru, sem Pattern.quote - caractere especial quebra a consulta
        if (StringUtils.isNotBlank(filter.getName())) {
            query.addCriteria(Criteria.where("name").regex(filter.getName().trim(), "i"));
        }

        return query;
    }
}
