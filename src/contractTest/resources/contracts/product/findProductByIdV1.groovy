// =============================================================================
// CONTRATO: findProductByIdV1.groovy
// =============================================================================
//
// Este arquivo é o CORAÇÃO do Contract-Driven Development.
// Ele representa um "acordo formal" entre duas partes:
//
//   - CONSUMER: o serviço que chama este endpoint (ex: algashop-ordering)
//   - PROVIDER: este serviço (product-catalog), que precisa honrar o acordo
//
// O Spring Cloud Contract lê este arquivo e:
//   1. Gera automaticamente um teste JUnit no PROVIDER (product-catalog)
//      para verificar que o endpoint realmente se comporta como descrito aqui.
//   2. Gera um "stub" (servidor fake) que o CONSUMER pode usar nos seus
//      próprios testes sem precisar subir o produto-catalog de verdade.
//
// CONVENÇÃO DE NOME: findProductByIdV1.groovy
//   - "findProductById" = nome descritivo da interação
//   - "V1"             = versão do contrato (permite evoluir sem quebrar consumidores antigos)
//   - ".groovy"        = linguagem do DSL do Spring Cloud Contract
//
// LOCALIZAÇÃO: src/contractTest/resources/contracts/product/
//   O subdiretório "product/" define qual classe base será usada nos testes gerados.
//   O framework procura uma classe chamada "ProductBase" no pacote configurado
//   em build.gradle (packageWithBaseClasses).
// =============================================================================

package contracts.product

import org.springframework.cloud.contract.spec.Contract

Contract.make {

    request {
        method GET()
        headers {
            accept 'application/json'
        }
        url("/api/v1/products/fffe6ec2-7103-48b3-8e4f-3b58e43fb75a")
    }

    response {
        status 200
        headers {
            contentType 'application/json'
        }
        body([
                id: fromRequest().path(3),
                addedAt: anyIso8601WithOffset(),
                name: 'Notebook X11',
                brand: 'Deep Diver',
                regularPrice: 1500.00,
                salePrice: 1000.00,
                inStock: true,
                enabled: true,
                category: [
                        id: anyUuid(),
                        name: 'Notebook'
                ],

                description: 'A Gamer Notebook'
        ])
    }
}
