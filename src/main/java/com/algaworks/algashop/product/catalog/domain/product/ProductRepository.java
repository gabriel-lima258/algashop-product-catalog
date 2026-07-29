package com.algaworks.algashop.product.catalog.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends MongoRepository<Product, UUID> {

    // Projecao no SERVIDOR: o fields diz ao Mongo para devolver so _id e name.
    // Menos dado trafegando que a projecao do ProductSummaryOutput, que traz o
    // documento inteiro e so depois recorta na aplicacao com o ModelMapper.
    //   value  -> o filtro; ?0 e o primeiro parametro do metodo
    //   fields -> quais campos vem na resposta (1 = inclui). _id sempre vem.
    @Query(value = "{'enabled': ?0}", fields = "{'name': 1}")
    Page<ProductNameProjection> findAllByEnabled(Boolean enabled, Pageable pageable);
}
