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
| **Cache** | Redis 8 (banco lógico 0) |
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

### Cache em duas camadas

O catálogo é lido muito mais do que escrito, então ele cacheia — **server-side**, no Redis, e autorizando o **cliente** a cachear por cabeçalho HTTP.

```java
// cache-aside: lê do Redis, cai no Mongo só no miss
@Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#productId")
ProductDetailOutput findById(UUID productId);

// write-through: grava no banco e no cache na mesma operação
@CachePut(cacheNames = CacheNames.PRODUCTS, key = "#result.id",
          condition = "#input.enabled == true")
public ProductDetailOutput create(ProductInput input) { ... }
```

Do lado do cliente, o `ETag` sai do `@Version` do documento — o validador já existia, e ele só muda quando o Mongo grava:

```java
return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
        .eTag("product:id" + product.getId() + ":v:" + product.getVersion())
        .lastModified(product.getUpdatedAt().toInstant())
        .body(product);
```

A listagem de categorias vai além e responde **304 sem corpo** quando nada mudou, comparando com um `max(updatedAt)` da coleção.

Duas decisões que valem mais que a implementação:

**Só o filtro default é cacheado.** Cachear listagem com filtro livre é armadilha de cardinalidade — cada combinação de nome, página e ordenação vira uma chave pedida uma vez só. A listagem de **produtos**, que tem busca textual e faixa de preço, não é cacheada pela mesma razão.

**Erro de cache é engolido.** O `ResilienceCacheErrorHandler` faz *fail-open*: Redis fora do ar significa "vou ao Mongo", não "desisti". Um cache que derruba o serviço ao cair inverteu a própria razão de existir.

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

Isso sobe **três nós** de MongoDB (27017, 27018, 27019), um container efêmero que executa o `rs.initiate` e morre, e o **Redis** na 6379.

> ⚠️ O Redis precisa do `.env` na raiz do meta (`REDIS_PASSWORD=algashop`). Sem ele o Compose resolve a senha para string vazia, o Redis sobe sem autenticação e o cache nunca funciona — sem quebrar nada, porque o error handler engole a falha.

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


### Health check

```bash
curl -s localhost:8083/actuator/health | jq            # tudo
curl -s localhost:8083/actuator/health/readiness | jq  # só o essencial
```

O grupo `readiness` inclui **apenas o MongoDB** — o Redis fora do ar não tira a instância de rotação, só marca o serviço como `DEGRADED`. É um status inventado pelo projeto, posicionado entre `UNKNOWN` e `UP` no `status.order`.

> ⚠️ `DEGRADED` devolve **HTTP 200**: só `DOWN` e `OUT_OF_SERVICE` viram 503 por padrão. Um probe que olhe o código de status não vê diferença.

Detalhes em [Health check e degradação](https://github.com/gabriel-lima258/algashop-docs/blob/main/04-infraestrutura/health-checks.md).

---

## Imagem Docker

```bash
./gradlew bootJar
docker build -t gabriel58221/product-catalog:dev .
```

Ou multi-arquitetura com push:

```bash
./gradlew dockerBuild
```

A base é `eclipse-temurin:25-jre`, acompanhando o toolchain do `build.gradle`. O `ENV JAR_NAME` do Dockerfile tem que casar com o `bootJar { archiveFileName }` — o `ADD` copia `build/libs/$JAR_NAME`.

Este serviço foi o último dos quatro a ganhar imagem, e com ela entrou no `docker-compose.services.yml`, esperando o `algashop-mongodb-init` terminar (`condition: service_completed_successfully`): os nós Mongo ficam *healthy* antes de o replica set existir, então esperar por eles não bastaria.


### Imagens de produto — o upload não passa por aqui

O catálogo **nunca vê os bytes**. Ele autoriza, e o cliente envia direto ao S3:

```
1. POST /api/v1/upload-requests        -> uploadSignedUrl + remoteFileName + expiresAt
2. PUT  <uploadSignedUrl>              -> o CLIENTE envia o arquivo ao S3
3. POST /api/v1/products/{id}/images   -> { "remoteFileName": "..." }
```

O passo 3 confere no storage (`fileExists`) que o arquivo realmente chegou antes de anexar — sem isso, o produto guardaria referência para imagem que nunca subiu.

| Verbo | Path | O que faz |
|---|---|---|
| `GET` | `/api/v1/products/{id}/images` | lista as imagens |
| `GET` | `/api/v1/products/{id}/images/{imageId}` | uma imagem |
| `POST` | `/api/v1/products/{id}/images` | anexa uma já enviada → `201` |
| `PUT` | `/api/v1/products/{id}/images/{imageId}/primary` | define a principal → `204` |
| `DELETE` | `/api/v1/products/{id}/images/{imageId}` | remove do produto **e do bucket** → `204` |

Em desenvolvimento o S3 é o **LocalStack** (porta 4566), com bucket, CORS e imagens de exemplo criados sozinhos na subida do compose. Para rodar sem ele:

```yaml
algashop:
  storage:
    provider: fake
```

> ⚠️ A URL assinada aponta para `algashop-localstack:4566` e vai para o navegador — as três linhas de LocalStack no arquivo `hosts` são o que a tornam alcançável.

Detalhes em [Armazenamento de arquivos](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/armazenamento-de-arquivos.md).


### Segurança — escopos exigidos

Este serviço é um **resource server**: toda rota exige `Authorization: Bearer <jwt>`, validado contra o issuer `http://algashop-authorization-server:9000`. Só `/actuator/health/**` é público.

| Rota | Escopo |
|---|---|
| `GET /api/v1/products`, `/products/{id}`, imagens | `products:read` |
| `POST`/`PUT`/`DELETE` de produto, imagens e upload | `products:write` |
| `POST /api/v1/products/{id}/restock` e `/withdraw` | **`products:stock:write`** |
| `GET /api/v1/categories`, `/categories/{id}` | `categories:read` |
| `POST`/`PUT`/`DELETE` de categoria | `categories:write` |

Estoque tem escopo **próprio**, separado da escrita de catálogo: quem integra estoque não ganha de brinde o direito de reescrever preço. Há um teste provando que `products:write` **não** abre `/restock` nem `/withdraw`.

```bash
TOKEN=$(curl -s -u algashop-test:testing123 -d grant_type=client_credentials \
  http://localhost:9000/oauth2/token | jq -r .access_token)

curl -s -H "Authorization: Bearer $TOKEN" localhost:8083/api/v1/...
```

Sem token → **401**. Com token e sem o escopo → **403**. Detalhes em [Resource servers e escopos](https://github.com/gabriel-lima258/algashop-docs/blob/main/05-seguranca/resource-server-e-escopos.md).

---

## Documentação

Este é o serviço mais documentado do projeto. Em [`algashop-docs`](https://github.com/gabriel-lima258/algashop-docs):

- [MongoDB no product-catalog](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/product-catalog-mongo.md) — modelagem documental, UUID como `_id`, auditoria, lock otimista
- [Normalizado × desnormalizado](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/desnormalizacao-mongo.md) — quando duplicar dado é decisão, e o que ela cobra
- [Concorrência e atomicidade](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/concorrencia-e-atomicidade.md) — lost update, `findAndModify`, `$inc` como delta
- [Transações e replica set](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/transacoes-mongo.md) — por que transação exige cluster, e quando ela não acrescenta nada
- [Eventos e listeners](https://github.com/gabriel-lima258/algashop-docs/blob/main/01-arquitetura-design/eventos-e-listeners.md) — os três mecanismos, e o que a consistência eventual custa
- [Cache](https://github.com/gabriel-lima258/algashop-docs/blob/main/01-arquitetura-design/cache.md) — cache-aside × write-through, invalidação e por que a idade de um dado é a soma das camadas
- [Armazenamento de arquivos](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/armazenamento-de-arquivos.md) — URL pré-assinada e o upload que não passa pelo backend
- [Health check e degradação](https://github.com/gabriel-lima258/algashop-docs/blob/main/04-infraestrutura/health-checks.md) — liveness × readiness e o status DEGRADED
- [Resource servers e escopos](https://github.com/gabriel-lima258/algashop-docs/blob/main/05-seguranca/resource-server-e-escopos.md) — escopo por rota, 401 × 403 e a matriz de testes
- [Redis na prática](https://github.com/gabriel-lima258/algashop-docs/blob/main/04-infraestrutura/redis.md) — eviction, TTL, inspeção e a armadilha da senha vazia
- [Consultas com Criteria](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/consultas-mongo-criteria.md) · [Índices](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/indices-mongo.md) · [Aggregation Pipeline](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/agregacoes-mongo.md)
- [Carga de dados](https://github.com/gabriel-lima258/algashop-docs/blob/main/04-infraestrutura/carga-de-dados-mongo.md) · [Ambiente local](https://github.com/gabriel-lima258/algashop-docs/blob/main/04-infraestrutura/ambiente-local.md)
