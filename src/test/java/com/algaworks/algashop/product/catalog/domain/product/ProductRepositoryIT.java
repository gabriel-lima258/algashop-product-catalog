package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.TestContainerMongoDBConfig;
import com.algaworks.algashop.product.catalog.infrastructure.persistence.MongoConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

// @DataMongoTest: fatia de contexto - sobe so a camada de persistencia (repositorios,
// conversores, template), sem controllers nem services. Contexto menor, teste mais rapido.
//
// @Import(MongoConfig.class): a fatia NAO carrega @Configuration da aplicacao.
// Sem esse import faltariam o UuidRepresentation.STANDARD e os conversores de
// OffsetDateTime, e o teste quebraria na leitura dos documentos.
//
// Sufixo IT: joga a classe na task integrationTest do Gradle, fora do ./gradlew test.
// Sobe o proprio Mongo, num container descartavel (TestContainerMongoDBConfig) - nao
// depende mais do docker-compose.tools.yml estar de pe. Os dados vem do DataLoader.
@DataMongoTest
@Import({MongoConfig.class, TestContainerMongoDBConfig.class})
@Slf4j
class ProductRepositoryIT {

    @Autowired
    private ProductRepository productRepository;

    // exercita a projecao: so id e name devem chegar preenchidos
    @Test
    void shouldFilterProduct() {
        Page<ProductNameProjection> products = productRepository.findAllByEnabled(true, PageRequest.of(0, 3));
        products.forEach(p -> log.info("Products id: {} - Name: {}", p.id(), p.name()));
    }
}