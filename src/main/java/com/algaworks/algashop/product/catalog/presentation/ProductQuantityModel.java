package com.algaworks.algashop.product.catalog.presentation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// Corpo de POST /{productId}/restock e /withdraw. Um objeto para um campo so em vez de
// um @RequestParam: quantidade e a primeira coisa que vai crescer (motivo do ajuste,
// numero da nota, deposito de origem), e mudar um corpo JSON e mais barato que mudar a
// assinatura da URL.
//
// O @Min(1) barra quantidade zero ou negativa na borda, com 400 e mensagem de validacao,
// antes de qualquer ida ao banco. O StockService repete a checagem de proposito - a borda
// protege a API, o dominio se protege de qualquer outro chamador
@Data
public class ProductQuantityModel {

    @NotNull
    @Min(1)
    private Integer quantity;
}
