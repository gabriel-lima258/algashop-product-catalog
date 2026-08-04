package com.algaworks.algashop.product.catalog.domain.product;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

// Vazio, e continua sendo o suficiente: hoje o unico uso e save(), chamado pelo
// application service dentro da transacao. Metodo de consulta so entra quando existir
// endpoint de historico - interface de repositorio cresce por demanda, nao por antecipacao.
//
// O save() aqui e escrita de UM documento novo, entao ele proprio nao precisaria de
// transacao. O @Transactional que o envolve nao existe para proteger esta linha, e sim
// para amarra-la ao findAndModify que ja ajustou o produto: e o par que precisa cair
// junto, nao cada metade. Ver transacoes-mongo.md.
public interface StockMovementRepository extends MongoRepository<StockMovement, UUID> {
}
