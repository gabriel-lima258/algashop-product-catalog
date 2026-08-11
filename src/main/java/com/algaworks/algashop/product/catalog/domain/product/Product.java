package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.domain.DomainException;
import com.algaworks.algashop.product.catalog.domain.category.Category;
import com.algaworks.algashop.product.catalog.domain.util.IdGenerator;
import io.micrometer.common.util.StringUtils;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.TextScore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Document(collection = "products")
@Getter
// callSuper = false explicito: o Product agora estende AbstractAggregateRoot, e sem isso
// o Lombok avisa. A identidade e o id e mais nada - a lista de eventos pendentes da
// superclasse e estado transitorio, e dois produtos de mesmo id sao o mesmo produto
// independentemente do que esteja enfileirado neles
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
// construtor sem argumentos protegido: o Spring Data instancia por reflexao mesmo assim,
// mas ninguem de fora consegue criar um Product "vazio" desviando do builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// Indices compostos da listagem. A ordem dos campos segue a regra ESR:
// Equality (category.id, enabled) -> Sort/Range (salePrice, addedAt).
// Sao dois porque um indice so consegue servir bem UMA dessas pontas por consulta:
// o primeiro cobre a faixa de preco, o segundo cobre a ordenacao por data.
// O -1 do addedAt e do mais recente ao mais antigo - o Mongo percorre o indice
// nos dois sentidos, entao ele atende ASC tambem.
// partialFilter: so indexa documento ativo, o que deixa o indice bem menor.
// ATENCAO: em troca, o Mongo so escolhe esse indice quando a consulta manda
// enabled: true EXPLICITO - cliente que omite o filtro cai em varredura.
//
// ATENCAO 2: aqui se escreve 'category.id', mas o indice NASCE como 'category._id'.
// Quem traduz e o mapping context do Spring Data: a propriedade chamada id de um objeto
// embutido vira _id no documento, e a resolucao do path vale tambem para a def do
// @CompoundIndex e para os Criteria. Conferir com db.products.getIndexes() -
// ver docs/02-persistencia/desnormalizacao-mongo.md
@CompoundIndex(name = "pidx_product_by_category_enabledTrue_salePrice",
        def = "{'category.id': 1, 'enabled': 1, 'salePrice': 1}",
        partialFilter = "{'enabled': true}")
@CompoundIndex(name = "pidx_product_by_category_enabledTrue_addedAt",
        def = "{'category.id': 1, 'enabled': 1, 'addedAt': -1}",
        partialFilter = "{'enabled': true}")
// AbstractAggregateRoot: da ao agregado um registerEvent() e uma lista @Transient de
// eventos pendentes (transiente, entao nao vai para o documento).
// ATENCAO ao momento da publicacao: quem publica NAO e o registerEvent, e sim o
// EventPublishingRepositoryProxyPostProcessor do Spring Data, DEPOIS de um save() feito
// pelo ProductRepository - e ele limpa a lista em seguida.
// Consequencia pratica: agregado que nunca passa pelo repositorio nao publica nada.
// Escrita por MongoTemplate/MongoOperations (o updateMulti do ProductCategoryUpdater,
// por exemplo) e invisivel para este mecanismo.
// Ver docs/01-arquitetura-design/eventos-e-listeners.md
public class Product extends AbstractAggregateRoot<Product> {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    // Busca textual: o Mongo aceita UM UNICO indice de texto por colecao, entao
    // todo campo @TextIndexed entra no mesmo indice. O weight pesa a relevancia de
    // cada campo no calculo do score - com os dois em 1, achar no nome vale o mesmo
    // que achar na descricao (peso so significa alguma coisa se os valores diferirem)
    @TextIndexed(weight = 1)
    private String name;

    @Indexed(name = "idx_product_by_brand")
    private String brand;

    @TextIndexed(weight = 1)
    private String description;

    private Integer quantityInStock = 0;

    private Boolean enabled;

    private BigDecimal regularPrice;

    private BigDecimal salePrice;

    @CreatedDate
    private OffsetDateTime addedAt;

    @LastModifiedDate
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    @CreatedBy
    private UUID createdByUserId;

    @LastModifiedBy
    private UUID lastModifiedByUserId;

    // JEITO 1 (normalizado): a categoria era uma REFERENCIA - o documento guardava so o
    // categoryId e quem quisesse o nome dela fazia uma leitura a mais (o N+1), ou um
    // $lookup no pipeline. Mantido comentado de proposito, como referencia de estudo;
    // a comparacao completa dos dois jeitos esta em
    // docs/02-persistencia/desnormalizacao-mongo.md
//    @DocumentReference
//    @Field(name = "categoryId")
//    private UUID categoryId;

    // JEITO 2 (desnormalizado, o atual): a categoria vira um value object EMBUTIDO no
    // proprio documento de produto - { category: { _id, name, enabled } }. A listagem
    // passa a ler nome e situacao da categoria sem tocar na colecao categories: nao ha
    // N+1 nem join a resolver.
    // O preco: e uma COPIA, e copia envelhece. Renomear uma categoria agora exige
    // reescrever todos os produtos dela - e quem faz isso e o CategoryEventListener,
    // de forma assincrona (consistencia eventual)
    private ProductCategory category;

    private Integer discountPercentageRounded;

    // campo de leitura: o MongoDB calcula a relevancia ($meta: "textScore") de cada documento
    // em buscas textuais (TextCriteria sobre os campos @TextIndexed) e o Spring Data preenche
    // aqui. Nao e persistido na collection - fora de uma busca textual chega null.
    // E por causa do @TextScore que o Sort.by("score") do ProductQueryServiceImpl vira
    // { score: { $meta: "textScore" } } em vez de ordenar por um campo inexistente.
    // A direcao do Sort nao importa: ordenacao por textScore no Mongo e sempre decrescente
    @TextScore
    private Float score;

    // A imagem principal e uma REFERENCIA para um dos elementos de images, nao uma
    // copia - as invariantes abaixo dependem disso. Set porque a ordem nao significa
    // nada aqui e a identidade e o id do Image (ver o equals da classe)
    private Image mainImage;

    private Set<Image> images = new HashSet<>();

    @Builder
    public Product(String name, String brand, Boolean enabled, BigDecimal regularPrice,
                   BigDecimal salePrice, String description, Category category) {
        this.setId(IdGenerator.generateTimeBasedUUID());
        this.setName(name);
        this.setBrand(brand);
        this.setDescription(description);
        this.setEnabled(enabled);
        // valida ANTES de aplicar: os setters de preco sao privados e passaram a ser
        // meros aplicadores de estado, entao a regra do PAR mora aqui e no changePrice
        validatePrices(regularPrice, salePrice);
        this.setRegularPrice(regularPrice);
        this.setSalePrice(salePrice);
        this.setCategory(category);

        registerProductAddedEvent();
    }

    public void setName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException();
        }
        this.name = name;
    }

    public void setBrand(String brand) {
        if (StringUtils.isBlank(brand)) {
            throw new IllegalArgumentException();
        }
        this.brand = brand;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    private void setRegularPrice(BigDecimal regularPrice) {
        Objects.requireNonNull(regularPrice);

        // se o numero for negativo
        if (regularPrice.signum() == -1) {
            throw new IllegalArgumentException();
        }

        this.regularPrice = regularPrice;
        this.calculateDiscountPercentage();
    }

    private void setSalePrice(BigDecimal salePrice) {
        Objects.requireNonNull(regularPrice);

        if (salePrice.signum() == -1) {
            throw new IllegalArgumentException();
        }

        this.salePrice = salePrice;
        this.calculateDiscountPercentage();
    }

    // emite evento so quando a situacao MUDA, e a comparacao e com o valor anterior.
    // as duas guardas nao sao decoracao:
    // - wasEnabled != null distingue "produto sendo criado" de "produto sendo alterado".
    //   sem ela, todo produto nascido com enabled=true emitiria um Listed logo apos o
    //   ProductAddedEvent, dizendo que foi listado algo que acabou de existir
    // - comparar antes/depois evita evento em chamada idempotente: mandar disable() num
    //   produto ja inativo nao aconteceu nada, entao nao ha o que anunciar
    public void setEnabled(Boolean enabled) {
        Objects.requireNonNull(enabled);
        Boolean wasEnabled  = this.enabled;
        this.enabled = enabled;

        if (wasEnabled != null && wasEnabled && !this.getEnabled()) {
            registerDelistedProductEvent();
        } else if (wasEnabled != null && !wasEnabled && this.getEnabled()) {
            registerListedProductEvent();
        }
    }

    public void disable() {
        this.setEnabled(false);
    }

    public void enable() {
        this.setEnabled(true);
    }

    private void setId(UUID id) {
        Objects.requireNonNull(id);
        this.id = id;
    }

    // recebe a Category (o agregado de verdade, carregado pelo application service) e
    // guarda so a COPIA reduzida. e aqui que a desnormalizacao acontece: a partir deste
    // ponto o Product nao depende mais da colecao categories para se descrever
    public void setCategory(Category category) {
        Objects.requireNonNull(category);
        this.category = ProductCategory.of(category);
    }

    public boolean isInStock() {
        return this.getQuantityInStock() != null && this.getQuantityInStock() > 0;
    }

    public boolean getHasDiscount() {
        return getDiscountPercentageRounded() != null && getDiscountPercentageRounded() > 0;
    }

    // Imutavel de proposito: quem tem o agregado na mao nao adiciona nem remove imagem
    // por fora - so por addImage/removeImage, que e onde as invariantes vivem.
    public Set<Image> getImages() {
        return Collections.unmodifiableSet(this.images);
    }

    public Optional<Image> getImage(UUID imageId) {
        Objects.requireNonNull(imageId);
        return this.images.stream().filter(image -> image.getId().equals(imageId)).findFirst();
    }

    public void changeMainImage(UUID imageId) {
        Objects.requireNonNull(imageId);
        // valida primeiramente
        Image image = findImageById(imageId);
        setMainImage(image);
    }



    // Devolve o id gerado porque quem chama precisa dele para localizar a imagem
    // recem-criada - o Image nasce aqui dentro, o chamador so passou o nome.
    public UUID addImage(String imageName) {
        Objects.requireNonNull(imageName);

        Image image = new Image(imageName);
        this.images.add(image);

        // A primeira imagem vira a principal sozinha. Sem isto, um produto com imagem
        // ficaria sem mainImage ate alguem escolher uma, e a vitrine nao teria o que
        // exibir - um estado valido em Java e invalido para o negocio.
        if (this.mainImage == null) {
            this.setMainImage(image);
        }

        return image.getId();
    }

    public void removeImage(UUID imageId) {
        Objects.requireNonNull(imageId);
        Image image = findImageById(imageId);
        this.images.remove(image);

        // A outra metade da mesma invariante: mainImage nunca aponta para imagem que
        // saiu da colecao. Remover a principal PROMOVE outra; se nao sobrar nenhuma,
        // volta a null - que e o unico caso legitimo de produto sem imagem principal.
        // Qualquer uma serve como sucessora: nao ha criterio de negocio para a escolha.
        if (image.equals(this.mainImage)) {
            this.setMainImage(this.images.stream().findFirst().orElse(null));
        }
    }

    private void setMainImage(Image mainImage) {
        this.mainImage = mainImage;
    }

    // operacao de negocio que substituiu os dois setters publicos de preco. existe porque
    // os precos NAO sao independentes: a regra so pode ser avaliada com o par completo em
    // maos, e quem chamasse setSalePrice sozinho conseguia deixar o agregado invalido.
    // tambem e o unico lugar que sabe distinguir "mudou de preco" de "entrou em promocao",
    // que sao dois eventos diferentes
    public void changePrice(BigDecimal regularPrice, BigDecimal salePrice) {
        Objects.requireNonNull(regularPrice);
        Objects.requireNonNull(salePrice);

        BigDecimal oldRegularPrice = this.regularPrice;
        BigDecimal oldSalePrice = this.salePrice;

        boolean wasOnSale = getHasDiscount();

        // valida o par NOVO contra ele mesmo. comparar o regularPrice novo com o salePrice
        // ANTIGO dava errado nas duas direcoes: rejeitava baixar os dois precos juntos
        // (3000/2789 -> 2500/2400) e deixava passar 3000/5000, que grava desconto negativo
        validatePrices(regularPrice, salePrice);

        setRegularPrice(regularPrice);
        setSalePrice(salePrice);

        // salvar o mesmo preco de novo nao e um fato: sai sem registrar nada.
        // sem esta guarda, um PUT repetido geraria uma enxurrada de PriceChanged identicos
        if (pricesDidNotChange(oldRegularPrice, oldSalePrice)) {
            return;
        }

        registerPriceChangedEvent(oldRegularPrice, oldSalePrice);

        if (isNewlyOnSale(wasOnSale)) {
            registerProductPlacedOnSaleEvent();
        }
    }

    private void setQuantityInStock(Integer quantityInStock) {
        Objects.requireNonNull(quantityInStock);

        if (quantityInStock < 0) {
            throw new IllegalArgumentException();
        }

        this.quantityInStock = quantityInStock;
    }

    private void calculateDiscountPercentage() {
        if (regularPrice == null || salePrice == null || regularPrice.signum() == 0) {
            discountPercentageRounded = 0;
            return;
        }

        discountPercentageRounded = BigDecimal.ONE
                .subtract(salePrice.divide(regularPrice, 4, RoundingMode.HALF_DOWN))
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_DOWN)
                .intValue();
    }

    // unico lugar onde vive a regra "promocao nao pode custar mais que o preco cheio".
    // e chamado pelo construtor e pelo changePrice, sempre sobre o par completo -
    // um setter sozinho nao consegue avaliar essa regra, porque so enxerga metade dela
    private void validatePrices(BigDecimal regularPrice, BigDecimal salePrice) {
        if (salePrice.compareTo(regularPrice) > 0) {
            throw new DomainException("Sale price cannot be greater than regular price");
        }
    }

    private boolean isNewlyOnSale(boolean wasOnSale) {
        return getHasDiscount() && !wasOnSale;
    }

    private boolean pricesDidNotChange(BigDecimal oldRegularPrice, BigDecimal oldSalePrice) {
        return Objects.equals(this.regularPrice, oldRegularPrice) && Objects.equals(this.salePrice, oldSalePrice);
    }

    private Image findImageById(UUID imageId) {
        return getImage(imageId).orElseThrow(() ->
                new DomainException(String.format("Image of id %s was not found on product %s", imageId, id))
        );
    }

    // Os cinco registradores abaixo so ENFILEIRAM o evento. Nada sai daqui ate alguem
    // chamar productRepository.save(this) - ver o comentario do topo da classe.
    // Sao privados e chamados de dentro das operacoes de negocio de proposito: quem decide
    // que houve um fato relevante e o agregado, nao quem o manipula de fora
    private void registerProductAddedEvent() {
        super.registerEvent(
                ProductAddedEvent.builder()
                        .productId(this.id)
                        .build()
        );
    }

    private void registerPriceChangedEvent(BigDecimal oldRegularPrice, BigDecimal oldSalePrice) {
        super.registerEvent(
                ProductPriceChangedEvent.builder()
                        .productId(this.id)
                        .newRegularPrice(this.regularPrice)
                        .newSalePrice(this.salePrice)
                        .oldRegularPrice(oldRegularPrice)
                        .oldSalePrice(oldSalePrice)
                        .build()
        );
    }

    private void registerProductPlacedOnSaleEvent() {
        super.registerEvent(
                ProductPlacedOnSaleEvent.builder()
                        .productId(this.id)
                        .regularPrice(this.regularPrice)
                        .salePrice(this.salePrice)
                        .build()
        );
    }

    private void registerListedProductEvent() {
        super.registerEvent(
                ProductListedEvent.builder()
                        .productId(this.id)
                        .build()
        );
    }

    private void registerDelistedProductEvent() {
        super.registerEvent(
                ProductDelistedEvent.builder()
                        .productId(this.id)
                        .build()
        );
    }

}
