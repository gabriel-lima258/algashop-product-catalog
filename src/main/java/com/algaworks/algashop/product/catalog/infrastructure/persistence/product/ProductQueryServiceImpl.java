package com.algaworks.algashop.product.catalog.infrastructure.persistence.product;

import com.algaworks.algashop.product.catalog.application.product.query.ProductDetailOutput;
import com.algaworks.algashop.product.catalog.application.product.query.ProductFilter;
import com.algaworks.algashop.product.catalog.application.product.query.ProductQueryService;
import com.algaworks.algashop.product.catalog.application.product.query.ProductSummaryOutput;
import com.algaworks.algashop.product.catalog.application.util.Mapper;
import com.algaworks.algashop.product.catalog.application.util.PageModel;
import com.algaworks.algashop.product.catalog.domain.product.Product;
import com.algaworks.algashop.product.catalog.domain.product.ProductNotFoundException;
import com.algaworks.algashop.product.catalog.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
public class ProductQueryServiceImpl implements ProductQueryService {

    private final ProductRepository productRepository;
    private final Mapper mapper;

    // criteria query
    private final MongoOperations mongoOperations;

    @Override
    public ProductDetailOutput findById(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return mapper.convert(product, ProductDetailOutput.class);
    }

    // A listagem NAO usa find() + ModelMapper como o findById aqui em cima: ela monta um
    // aggregation pipeline, uma esteira de estagios em que a saida de um e a entrada do
    // proximo. Os dois jeitos convivem de proposito - o simples continua no findById e
    // nas categorias.
    //
    // ATENCAO ao que sobrou: o pipeline nasceu para juntar a colecao de categorias com
    // $lookup e acabar com o N+1 do @DocumentReference. Esse motivo ACABOU - a categoria
    // hoje esta embutida no proprio documento, entao nao ha nada a juntar. O que ainda
    // justifica o pipeline e so o $project la embaixo, que calcula os campos derivados no
    // servidor. Ver docs/02-persistencia/desnormalizacao-mongo.md
    @Override
    public PageModel<ProductSummaryOutput> filter(ProductFilter filter) {
        // 1. monta os filtros. Optional e nao Criteria direto: um new Criteria() vazio vira
        // documento {} no $match e casaria tudo - o Optional deixa "nao ha filtro" explicito
        Optional<Criteria> criteria = buildCriteria(filter);
        Optional<TextCriteria> textCriteria = buildTextCriteria(filter);

        // 2. conta o total com uma Query COMUM, fora do pipeline - mesmos filtros, sem os
        // estagios de projecao e paginacao.
        // Esta contagem ja divergiu do resultado: enquanto havia $unwind (inner join),
        // produto de categoria orfa entrava na conta e sumia da pagina. Com a categoria
        // embutida nao ha mais join, e as duas contas nao tem mais como discordar
        Query query = new Query();
        textCriteria.ifPresent(query::addCriteria);
        criteria.ifPresent(query::addCriteria);

        long totalElements = mongoOperations.count(query, Product.class);

        // atalho: nada casou, entao nem monta o pipeline. o size/number ecoam o que o
        // cliente pediu (e o que o contrato afirma), e o content vai vazio em vez de null
        if (totalElements == 0L) {
            return PageModel.<ProductSummaryOutput>builder()
                    .content(List.of())
                    .number(filter.getPage())
                    .size(filter.getSize())
                    .totalPages(0)
                    .totalElements(0)
                    .build();
        }

        // 3. monta o pipeline, estagio a estagio
        List<AggregationOperation> operations = new ArrayList<>();

        // o $text tem que ser o PRIMEIRO estagio do pipeline (regra do Mongo), por isso ele
        // entra antes dos demais criterias.
        // e o $addFields logo depois nao e opcional: dentro do pipeline o @TextScore do Product
        // nao vale, entao o campo "score" precisa ser criado na mao com $meta: "textScore",
        // senao nao existe nada para o $sort ordenar por relevancia.
        // vai como lambda porque o Spring Data nao tem operacao pronta de $addFields com $meta -
        // qualquer AggregationOperation e so uma funcao que devolve o Document do estagio
        textCriteria.ifPresent(c -> {
            operations.add(Aggregation.match(c));
            AggregationOperation addTextScoreField = context ->
                    new Document("$addFields", new Document("score", new Document("$meta", "textScore")));
            operations.add(addTextScoreField);
        });
        criteria.ifPresent(c -> operations.add(Aggregation.match(c)));

        PageRequest pageRequest = PageRequest.of(filter.getPage(), filter.getSize());

        // JEITO 1 (normalizado), mantido comentado como referencia de estudo:
        // lookup = join com a colecao categories pelo categoryId; unwind desembrulha o array
        // de um elemento que o lookup produz, virando um objeto so. Era o que acabava com o
        // N+1 do @DocumentReference - cada produto que tocasse getCategory() lia de novo.
        // Tinha duas pegadinhas: unwind sem preserveNullAndEmptyArrays e INNER JOIN, entao
        // produto de categoria orfa sumia do resultado sem aviso; e o join rodava sobre
        // TODOS os documentos filtrados, nao sobre os 15 da pagina.
        // Os dois estagios foram aposentados pela desnormalizacao, nao por serem ruins:
        // sem categoria em outra colecao, nao ha o que juntar. Comparacao completa em
        // docs/02-persistencia/desnormalizacao-mongo.md
        //
        // ATENCAO ao que continua valendo: o $project abaixo ainda roda sobre todos os
        // documentos filtrados, antes do $skip/$limit. Projetar depois de paginar seria
        // mais barato - segue como pendencia registrada
        operations.addAll(Arrays.asList(
//                lookup("categories", "categoryId", "_id", "category"),
//                unwind("$category"),
                sort(sortWith(filter)),
                projectionForSummary(),
                skip(pageRequest.getOffset()),
                limit(filter.getSize())
        ));

        Aggregation aggregation = newAggregation(operations);

        // 4. executa: le da colecao de Product, materializa em ProductSummaryOutput.
        // o $project ja devolve os campos com o nome do DTO, entao nao passa por ModelMapper
        List<ProductSummaryOutput> productSummaryOutputs = mongoOperations
                .aggregate(aggregation, Product.class, ProductSummaryOutput.class)
                .getMappedResults();

        int totalPages = (int) Math.ceil((double) totalElements / (double) filter.getSize());

        return PageModel.<ProductSummaryOutput>builder()
                .content(productSummaryOutputs)
                .number(filter.getPage())
                .size(filter.getSize())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    // filtro dinamico: cada campo preenchido vira um criteria a mais na lista.
    // campo null e ignorado (nao filtra); no fim tudo vira um $and so.
    // mudou de forma em relacao ao jeito com Query: la os criterias iam sendo empilhados
    // com addCriteria na propria Query; aqui eles sao acumulados numa lista, porque o
    // resultado precisa ser UM criteria unico para caber dentro de um estagio $match
    private Optional<Criteria> buildCriteria(ProductFilter filter) {
        // List<Criteria>, nao List<CriteriaDefinition>: o andOperator so aceita Criteria,
        // entao a lista mais larga deixava o erro escapar para o runtime (ArrayStoreException)
        List<Criteria> criterias = new ArrayList<>();

        // enabled -> { "enabled": true|false }
        // null traz ativos e inativos; true so os ativos; false so os inativos
        if (filter.getEnabled() != null) {
            criterias.add(Criteria.where("enabled").is(filter.getEnabled()));
        }

        // addedAt -> intervalo de data. Os dois extremos sao opcionais e independentes:
        // com ambos vira { "addedAt": { $gte: X, $lte: Y } }; com um so, fica aberto do outro lado.
        // os dois criterias precisam ser encadeados no MESMO where("addedAt") -
        // dois add(); no mesmo campo o Mongo rejeita como chave duplicada
        if (filter.getAddedAtFrom() != null && filter.getAddedAtTo() != null) {
            criterias.add(Criteria.where("addedAt")
                    .gte(filter.getAddedAtFrom())
                    .lte(filter.getAddedAtTo())
            );
        } else {
            if (filter.getAddedAtFrom() != null) {
                criterias.add(Criteria.where("addedAt").gte(filter.getAddedAtFrom()));
            } else if (filter.getAddedAtTo() != null) {
                criterias.add(Criteria.where("addedAt").lte(filter.getAddedAtTo()));
            }
        }

        // faixa de preco - mesma estrutura do addedAt.
        // filtra pelo salePrice (o que o cliente paga), nao pelo regularPrice
        if (filter.getPriceFrom() != null && filter.getPriceTo() != null) {
            criterias.add(Criteria.where("salePrice")
                    .gte(filter.getPriceFrom())
                    .lte(filter.getPriceTo())
            );
        } else {
            if (filter.getPriceFrom() != null) {
                criterias.add(Criteria.where("salePrice").gte(filter.getPriceFrom()));
            } else if (filter.getPriceTo() != null) {
                criterias.add(Criteria.where("salePrice").lte(filter.getPriceTo()));
            }
        }

        // hasDiscount -> compara DOIS CAMPOS do mesmo documento (salePrice x regularPrice).
        // Criteria comum so compara campo com valor literal, entao a comparacao vai dentro
        // de um $expr: { $expr: { $lt: ["$salePrice", "$regularPrice"] } }.
        // $expr e operador de query de primeira classe, entao da pra escreve-lo como um
        // Criteria normal - AggregationExpressionCriteria implementa CriteriaDefinition mas
        // nao Criteria, e por isso nao entra no andOperator la embaixo.
        // custo: $expr nao usa indice - varre a colecao inteira
        if (filter.getHasDiscount() != null) {
            AggregationExpression discountExpression = filter.getHasDiscount()
                    ? ComparisonOperators.valueOf("$salePrice").lessThan("$regularPrice")
                    : ComparisonOperators.valueOf("$salePrice").equalTo("$regularPrice");

            criterias.add(Criteria.where("$expr").is(discountExpression.toDocument()));
        }

        // inStock -> tem ou nao estoque. campo derivado nao existe no documento,
        // entao a regra do isInStock() e reescrita aqui em cima de quantityInStock
        if (filter.getInStock() != null) {
            if (filter.getInStock()) {
                criterias.add(Criteria.where("quantityInStock").gt(0));
            } else {
                criterias.add(Criteria.where("quantityInStock").is(0));
            }
        }

        // categoriesId -> { "category._id": { $in: [...] } }.
        // filtrar por categoria nao custa leitura extra - o dado ja esta no documento -
        // e isso nunca foi tao verdade quanto agora: antes o campo era o categoryId
        // gravado pelo @DocumentReference, hoje e o id da copia embutida.
        // ATENCAO: aqui se escreve "category.id" e o Mongo recebe "category._id" - o
        // QueryMapper resolve o path pelo mapping context, a mesma traducao que acontece
        // na def do @CompoundIndex do Product
        if (filter.getCategoriesId() != null && filter.getCategoriesId().length > 0) {
            criterias.add(Criteria.where("category.id").in(
                    (Object[]) filter.getCategoriesId()
            ));
        }

        if (criterias.isEmpty()) {
            return Optional.empty();
        }

        // juntando todas as criterias dentro de unico objeto, pois por padrão só retorna uma.
        // overload de Collection em vez de toArray: sem array intermediario, sem chance
        // de o store no array estourar em tempo de execucao
        return Optional.of(new Criteria().andOperator(criterias));
    }

    // função auxiliar para converter o tipo text dentro da função principal de build criteria
    public Optional<TextCriteria> buildTextCriteria(ProductFilter filter) {
        // term -> { $text: { $search: "..." } }, servido pelo indice de texto (@TextIndexed
        // em name e description). Substituiu o $or de tres regex, que varria a colecao inteira.
        // o que se ganhou: usa indice, faz stemming e nao ha risco de ReDoS.
        // o que se perdeu: casa palavra inteira ("note" nao acha "notebook", entao busca
        // enquanto o usuario digita deixou de funcionar) e brand ficou de fora da busca
        if (StringUtils.isNotBlank(filter.getTerm())) {
            return Optional.of(TextCriteria.forDefaultLanguage().matching(filter.getTerm()));
        }

        return Optional.empty();
    }

    // ordenacao: o que o cliente pediu ou o default do ProductFilter (addedAt ASC)
    private Sort sortWith(ProductFilter filter) {
        // busca textual ignora o sort pedido e ordena por relevancia.
        // DESC explicito: dentro do pipeline o "score" e um campo comum, criado pelo
        // $addFields la em cima. Nao ha @TextScore valendo aqui, entao nada traduz para
        // $meta - e Sort.by("score") sozinho geraria { $sort: { score: 1 } }, que devolve
        // o MENOS relevante primeiro
        if (StringUtils.isNotBlank(filter.getTerm())) {
            return Sort.by(Sort.Direction.DESC, "score");
        }
        return Sort.by(filter.getSortDirectionOrDefault(),
                filter.getSortByPropertyOrDefault().getPropertyName());
    }

    // $project: escolhe o que sai do pipeline e ja monta o formato do ProductSummaryOutput.
    // a primeira metade so repassa campo cru; a segunda CALCULA, no servidor, o que antes
    // era conversor do ModelMapper rodando em Java depois da consulta
    private ProjectionOperation projectionForSummary() {
        return project()
                .and("_id").as("_id")
                .and("addedAt").as("addedAt")
                .and("name").as("name")
                .and("brand").as("brand")
                .and("regularPrice").as("regularPrice")
                .and("salePrice").as("salePrice")
                .and("enabled").as("enabled")
                .and("quantityInStock").as("quantityInStock")
                .and("discountPercentageRounded").as("discountPercentageRounded")
                .and("score").as("score")
                // vem do proprio documento, do subdocumento category embutido. ate a
                // desnormalizacao estes campos so existiam depois do $lookup - hoje sao
                // repasse puro, como qualquer outro campo cru acima
                .and("category._id").as("category._id")
                .and("category.name").as("category.name")
                .and("category.enabled").as("category.enabled")

                // campos derivados: a mesma regra do agregado, reescrita em operador do Mongo
                .andExpression("salePrice < regularPrice").as("hasDiscount")
                .andExpression("quantityInStock > 0").as("inStock")
                // substringCP e nao substring: o $substr corta por BYTE e o Mongo devolve erro
                // se o corte cair no meio de um caractere UTF-8 (trivial com "ç", "ã", "é").
                // $substrCP conta caractere, entao descricao com acento nao quebra
                .and(StringOperators.valueOf("description")
                        .substringCP(0, 50)).as("shortDescription");
    }
}
