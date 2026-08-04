# product-catalog

Catálogo de produtos e categorias do AlgaShop. O único serviço em **MongoDB** — e o mais denso tecnicamente dos quatro.

---

## O problema

Catálogo tem um padrão de acesso próprio: muito mais leitura que escrita, atributos que variam por tipo de produto, e volume que cresce. Isso justificou sair do PostgreSQL — e cada decisão tomada a partir daí só faz sentido à luz desse padrão.

Modelar em documento não é "salvar JSON". É decidir, a cada relacionamento, entre **referenciar** (normalizado, consistente, caro de ler) e **embutir** (desnormalizado, barato de ler, e que envelhece). Este serviço fez as duas escolhas, em fases diferentes, e a segunda foi para corrigir a primeira.

---

## Stack

| | |
|---|---|
| **Java** | 25 |
| **Spring Boot** | 4.0.1 |
| **Banco** | MongoDB 8 — replica set `rs0` de três nós |
| **Porta** | 8083 |
| **Pacote raiz** | `com.algaworks.algashop.product.catalog` |
| **Coleções** | `products`, `categories`, `stock_movements` |

> No Boot 4 a propriedade da conexão é **`spring.mongodb.uri`**, não `spring.data.mongodb.uri`. Praticamente todo tutorial ainda usa a antiga — e ela é silenciosamente ignorada, com sintoma de "conecta, mas a coleção está sempre vazia".

---

## O que há de interessante aqui

### Categoria embutida, mantida por evento

O produto guarda uma **cópia** dos dados da categoria, em vez de uma referência. A listagem deixou de precisar de `$lookup` e ficou trivial — e nasceu o problema clássico: cópia envelhece.

A propagação é feita por evento assíncrono. Alterar uma categoria dispara um `updateMulti` que reescreve a cópia em todos os produtos dela. É consistência eventual assumida, com o que ela **não** garante registrado por escrito.

### Estoque sem carregar o agregado

Dar baixa em estoque com `findById` → `if` → `save` está errado, e o erro não aparece em teste sequencial nenhum: entre conferir e gravar cabe a requisição de outra pessoa. Aqui a regra vai **dentro do filtro**:

```java
Query query = Query.query(byId(productId).and("quantityInStock").gte(quantity));
Update update = new Update().inc("quantityInStock", -quantity);
Product before = mongoOperations.findAndModify(query, update,
        new FindAndModifyOptions().returnNew(false), Product.class);
```

Ou o Mongo casa e decrementa numa operação indivisível, ou não casa e nada acontece. É *compare-and-set*: em vez de perguntar e depois agir, você age condicionalmente e descobre pelo resultado.

Coberto por teste com threads de verdade, soltas juntas por um `CountDownLatch` — sem isso o teste passa em qualquer implementação, inclusive na errada.

### Transação entre duas coleções

Ajustar o saldo e registrar a movimentação em `stock_movements` são duas escritas, e o Mongo só garante atomicidade de uma. Saldo certo que nenhum histórico explica é pior que saldo errado: não dá nem para auditar.

Daí o `@Transactional` e o `MongoTransactionManager` — e daí o **replica set de três nós no compose**, porque transação no MongoDB não existe fora dele. É um requisito de domínio que atravessou o sistema até a infraestrutura.

### Três caminhos diferentes para publicar evento

| Mecanismo | Quem publica | Quando |
|---|---|---|
| `AbstractAggregateRoot` | Spring Data | no `save()` do repositório |
| `ApplicationMessagePublisher` | o application service | na chamada a `send()` |
| `DomainEventPublisher` | o `StockService` | na chamada a `publish()` |

O terceiro existe por uma razão puramente técnica: o ajuste de estoque não chama `save()`, então o mecanismo do agregado nunca dispara.

---

## Modelo de domínio

| Documento | Coleção | Papel |
|---|---|---|
| `Product` | `products` | agregado raiz — `@Version`, auditoria, eventos |
| `Category` | `categories` | agregado raiz |
| `ProductCategory` | *(embutido)* | a cópia desnormalizada dentro do produto |
| `StockMovement` | `stock_movements` | registro imutável de entrada ou saída |

`StockMovement` é o oposto de `Product`, de propósito: sem `@Version`, sem `AbstractAggregateRoot`, sem auditoria. **Um registro imutável de fato consumado não tem invariante para proteger** — toda a maquinaria do agregado existe para defender regra sobre estado que muda, e aqui não há estado que mude.

**Eventos:** `ProductAddedEvent`, `ProductPriceChangedEvent`, `ProductPlacedOnSaleEvent`, `ProductListedEvent`, `ProductDelistedEvent`, `ProductRestockedEvent`, `ProductSoldOutEvent`, `CategoryUpdatedEvent`.

Os dois de estoque só saem quando a quantidade **cruza** o zero. Evento nasce de transição, não de estado — senão "está zerado" seria verdade a cada operação, e viraria uma enxurrada de eventos idênticos.

---

## API

### Produtos — `/api/v1/products`

| Verbo | Path | O que faz |
|---|---|---|
| `GET` | `/api/v1/products` | lista paginada, com filtro dinâmico e busca textual |
| `GET` | `/api/v1/products/{productId}` | detalhe |
| `POST` | `/api/v1/products` | cria → `201` |
| `PUT` | `/api/v1/products/{productId}` | atualiza |
| `PUT` | `/api/v1/products/{productId}/enable` | reativa → `204` |
| `DELETE` | `/api/v1/products/{productId}/enable` | desativa (soft delete) → `204` |
| `POST` | `/api/v1/products/{productId}/restock` | entrada de estoque → `204` |
| `POST` | `/api/v1/products/{productId}/withdraw` | saída de estoque → `204`, ou `422` se faltar saldo |

### Categorias — `/api/v1/categories`

| Verbo | Path | O que faz |
|---|---|---|
| `GET` | `/api/v1/categories` | lista paginada, com filtro |
| `GET` | `/api/v1/categories/{categoryId}` | detalhe |
| `POST` | `/api/v1/categories` | cria → `201` |
| `PUT` | `/api/v1/categories/{categoryId}` | atualiza — e dispara a propagação da cópia |
| `DELETE` | `/api/v1/categories/{categoryId}` | remove → `204` |

Erros seguem **RFC 7807**: `404` para recurso inexistente, `422` para regra de negócio — saldo insuficiente devolve `422` com a quantidade pedida e a disponível.

O contrato completo está em [`openapi/product-catalog.yml`](https://github.com/gabriel-lima258/algashop-docs/blob/main/openapi/product-catalog.yml).

---

## Como rodar

A partir do repositório [`algashop-meta`](https://github.com/gabriel-lima258/algashop-meta):

```bash
docker compose -f docker-compose.tools.yml up -d
```

Isso sobe **três nós** de MongoDB (27017, 27018, 27019) e um container efêmero que executa o `rs.initiate` e morre.

Os nós se anunciam pelos nomes internos do Docker, então sua máquina precisa saber resolvê-los. Acrescente ao arquivo `hosts`:

```
127.0.0.1       algashop-mongodb-1
127.0.0.1       algashop-mongodb-2
127.0.0.1       algashop-mongodb-3
```

O passo a passo por sistema operacional está em `etc/hostnames/` no repositório meta.

```bash
./gradlew bootRun
```

O serviço responde em `http://localhost:8083`. Na subida, o `DataLoader` recarrega `products` e `categories` a partir de `db/testdata/`.

> ⚠️ O perfil de desenvolvimento roda com `algashop.data-load.auto-drop: true` — as coleções são **apagadas e recriadas a cada inicialização**. Alterou um documento pelo `mongosh` e reiniciou? A alteração se foi.

Conferindo o cluster:

```bash
docker exec -it algashop-meta-algashop-mongodb-1-1 mongosh
rs.status()
```

---

## Testes

```bash
./gradlew test              # unitários e de fatia
./gradlew integrationTest   # classes *IT
./gradlew contractTest      # gerados a partir dos contratos .groovy
./gradlew check             # as três
```

Os `*IT` sobem o **próprio MongoDB** num container descartável (Testcontainers), com `withReplicaSet()` — sem ele o container roda como nó único e o teste de transação morre com `Transaction numbers are only allowed on a replica set member`. Nenhum teste depende do compose de pé, e nenhum consegue alcançar o banco de desenvolvimento.

São **17 contratos** em `src/contractTest/resources/contracts/` — 9 de produto, 8 de categoria. Eles geram os testes que verificam este serviço e o stub que o `ordering` consome para testar sem o catálogo de pé.

---

## Documentação

Este é o serviço mais documentado do projeto. Em [`algashop-docs`](https://github.com/gabriel-lima258/algashop-docs):

- [MongoDB no product-catalog](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/product-catalog-mongo.md) — modelagem documental, UUID como `_id`, auditoria, lock otimista
- [Normalizado × desnormalizado](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/desnormalizacao-mongo.md) — quando duplicar dado é decisão, e o que ela cobra
- [Concorrência e atomicidade](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/concorrencia-e-atomicidade.md) — lost update, `findAndModify`, `$inc` como delta
- [Transações e replica set](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/transacoes-mongo.md) — por que transação exige cluster, e quando ela não acrescenta nada
- [Eventos e listeners](https://github.com/gabriel-lima258/algashop-docs/blob/main/01-arquitetura-design/eventos-e-listeners.md) — os três mecanismos, e o que a consistência eventual custa
- [Consultas com Criteria](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/consultas-mongo-criteria.md) · [Índices](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/indices-mongo.md) · [Aggregation Pipeline](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/agregacoes-mongo.md)
- [Carga de dados](https://github.com/gabriel-lima258/algashop-docs/blob/main/04-infraestrutura/carga-de-dados-mongo.md) · [Ambiente local](https://github.com/gabriel-lima258/algashop-docs/blob/main/04-infraestrutura/ambiente-local.md)
