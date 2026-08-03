package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.domain.DomainException;

import java.util.UUID;

// Saldo insuficiente NAO e falha tecnica: e uma regra de negocio sendo aplicada, e o
// cliente precisa conseguir distinguir isso de "produto nao existe" ou "o banco caiu".
//
// Estende DomainException, entao o ApiExceptionHandler ja a traduz para 422. A mensagem
// diz quanto foi pedido e quanto havia, porque "nao deu" sem numero nao ajuda ninguem a
// decidir o que fazer em seguida.
//
// ATENCAO ao "havia": o valor e lido DEPOIS da tentativa que falhou, numa segunda ida ao
// banco - ou seja, e informativo, nao uma promessa. Entre a falha e a leitura o estoque
// pode ter mudado de novo. Repetir a operacao com essa quantidade nao tem garantia
// nenhuma de sucesso, e e assim mesmo: em concorrencia, saldo e sempre uma foto do passado
public class InsufficientStockException extends DomainException {

    public InsufficientStockException(UUID productId, int requested, int available) {
        super(String.format(
                "Insufficient stock for product %s: requested %d, available %d",
                productId, requested, available));
    }
}
