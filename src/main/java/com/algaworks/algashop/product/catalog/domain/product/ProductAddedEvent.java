package com.algaworks.algashop.product.catalog.domain.product;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

// Evento de DOMINIO: registrado pelo proprio agregado (no construtor do Product) e
// publicado pelo Spring Data quando o produto e salvo pelo repositorio.
//
// Carrega o id, nao o Product: evento e um fato passado, e um fato nao deve carregar um
// agregado mutavel que o consumidor possa alterar. Quem precisar do resto vai buscar.
//
// O @Builder.Default no instante e o que garante que ele exista mesmo quando ninguem
// informa - sem ele o Lombok ignoraria a inicializacao do campo e o evento sairia com
// addedAt nulo. Os cinco eventos deste pacote seguem esse mesmo formato
@Builder
@Getter
@ToString
public class ProductAddedEvent {

    private UUID productId;

    @Builder.Default
    private OffsetDateTime addedAt = OffsetDateTime.now();
}
