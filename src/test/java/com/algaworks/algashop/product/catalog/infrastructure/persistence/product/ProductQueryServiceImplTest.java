package com.algaworks.algashop.product.catalog.infrastructure.persistence.product;

import com.algaworks.algashop.product.catalog.application.product.query.ProductFilter;
import com.algaworks.algashop.product.catalog.application.util.Mapper;
import com.algaworks.algashop.product.catalog.domain.product.Product;
import com.algaworks.algashop.product.catalog.domain.product.ProductRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

// O filtro por hasDiscount precisa de $expr (compara dois campos do mesmo documento).
// Ele ja foi um AggregationExpressionCriteria, que implementa CriteriaDefinition mas nao
// Criteria - e o andOperator so aceita Criteria. Como a lista era de CriteriaDefinition,
// o compilador deixava passar e o toArray(new Criteria[0]) estourava ArrayStoreException
// em producao, so quando hasDiscount vinha na query string.
@ExtendWith(MockitoExtension.class)
class ProductQueryServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private Mapper mapper;

    @Mock
    private MongoOperations mongoOperations;

    @InjectMocks
    private ProductQueryServiceImpl productQueryService;

    @Test
    void shouldBuildQueryWhenFilteringByHasDiscount() {
        ProductFilter filter = new ProductFilter();
        filter.setHasDiscount(true);

        assertThatCode(() -> productQueryService.filter(filter)).doesNotThrowAnyException();

        Document criteria = capturedCriteria();

        assertThat(criteria).containsKey("$and");
        assertThat(criteria.toJson()).contains("$expr").contains("$lt");
    }

    @Test
    void shouldCombineHasDiscountWithOtherCriteria() {
        ProductFilter filter = new ProductFilter();
        filter.setHasDiscount(false);
        filter.setEnabled(true);
        filter.setPriceFrom(new BigDecimal("10"));

        assertThatCode(() -> productQueryService.filter(filter)).doesNotThrowAnyException();

        String json = capturedCriteria().toJson();

        assertThat(json).contains("$expr").contains("$eq").contains("enabled").contains("salePrice");
    }

    // conta zero de proposito: filter() sai cedo, entao o teste cobre a montagem da query
    // sem precisar de Mongo de pe nem de simular o pipeline de aggregation inteiro
    private Document capturedCriteria() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoOperations).count(captor.capture(), eq(Product.class));
        return captor.getValue().getQueryObject();
    }

    @Test
    void shouldNotAddExprWhenHasDiscountIsAbsent() {
        ProductFilter filter = new ProductFilter();
        filter.setEnabled(true);

        productQueryService.filter(filter);

        assertThat(capturedCriteria().toJson()).doesNotContain("$expr");
    }
}
