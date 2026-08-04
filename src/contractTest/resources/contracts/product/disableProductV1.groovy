// =============================================================================
// CONTRATO: disableProductV1.groovy
// =============================================================================
//
// Desativação de produto (soft delete).
//
// O produto NÃO é removido do catálogo — ele passa a ter enabled = false.
// Por isso a URL não é um DELETE no recurso em si, mas no SUB-RECURSO /enable:
//
//   DELETE /api/v1/products/{id}/enable  -> desativa (enabled = false)
//   PUT    /api/v1/products/{id}/enable  -> reativa  (enabled = true)
//
// O par PUT/DELETE sobre /enable expressa uma flag ligada/desligada com os verbos
// que o HTTP já tem, e ambos são idempotentes: chamar duas vezes tem o mesmo efeito
// que chamar uma. Um DELETE direto em /api/v1/products/{id} prometeria ao consumidor
// uma exclusão que este serviço não faz.
//
// PROVIDER: ProductController.disable() -> ProductManagementApplicationService.disable()
//
// CONVENÇÃO DE NOME: disableProductV1.groovy
//   - "disableProduct" = nome descritivo da interação
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
        method DELETE()
        headers {
            accept 'application/json'
        }
        url("/api/v1/products/f1d3a7c4-6b2e-4f8a-9217-5d9c2e1b3a5f/enable")
    }

    response {
        status 204
    }
}
