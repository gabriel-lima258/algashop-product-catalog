package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.domain.DomainException;
import com.algaworks.algashop.product.catalog.domain.category.Category;
import com.algaworks.algashop.product.catalog.domain.util.IdGenerator;
import io.micrometer.common.util.StringUtils;
import lombok.*;
import org.springframework.data.annotation.*;
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
import java.util.Objects;
import java.util.UUID;

@Document(collection = "products")
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
// construtor sem argumentos protegido: o Spring Data instancia por reflexao mesmo assim,
// mas ninguem de fora consegue criar um Product "vazio" desviando do builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// Indices compostos da listagem. A ordem dos campos segue a regra ESR:
// Equality (categoryId, enabled) -> Sort/Range (salePrice, addedAt).
// Sao dois porque um indice so consegue servir bem UMA dessas pontas por consulta:
// o primeiro cobre a faixa de preco, o segundo cobre a ordenacao por data.
// O -1 do addedAt e do mais recente ao mais antigo - o Mongo percorre o indice
// nos dois sentidos, entao ele atende ASC tambem.
// partialFilter: so indexa documento ativo, o que deixa o indice bem menor.
// ATENCAO: em troca, o Mongo so escolhe esse indice quando a consulta manda
// enabled: true EXPLICITO - cliente que omite o filtro cai em varredura
@CompoundIndex(name = "pidx_product_by_category_enabledTrue_salePrice",
        def = "{'categoryId': 1, 'enabled': 1, 'salePrice': 1}",
        partialFilter = "{'enabled': true}")
@CompoundIndex(name = "pidx_product_by_category_enabledTrue_addedAt",
        def = "{'categoryId': 1, 'enabled': 1, 'addedAt': -1}",
        partialFilter = "{'enabled': true}")
public class Product {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    // Busca textual: o Mongo aceita UM UNICO indice de texto por colecao, entao
    // todo campo @TextIndexed entra no mesmo indice. O weight pesa a relevancia de
    // cada campo no calculo do score - com os dois em 1, achar no nome vale o mesmo
    // que achar na descricao (peso so significa alguma coisa se os valores diferirem)
    @TextIndexed(weight = 1)
    private String name;

    // indice simples, criado com nome proprio para dar pra identificar no getIndexes().
    // ATENCAO: hoje nenhuma consulta filtra por marca - a busca por termo virou $text
    // sobre name/description - entao este indice so custa escrita e memoria
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

    // referencia de relação de outra classe
    @DocumentReference
    @Field(name = "categoryId")
    private Category category;

    private Integer discountPercentageRounded;

    // campo de leitura: o MongoDB calcula a relevancia ($meta: "textScore") de cada documento
    // em buscas textuais (TextCriteria sobre os campos @TextIndexed) e o Spring Data preenche
    // aqui. Nao e persistido na collection - fora de uma busca textual chega null.
    // E por causa do @TextScore que o Sort.by("score") do ProductQueryServiceImpl vira
    // { score: { $meta: "textScore" } } em vez de ordenar por um campo inexistente.
    // A direcao do Sort nao importa: ordenacao por textScore no Mongo e sempre decrescente
    @TextScore
    private Float score;

    @Builder
    public Product(String name, String brand, Boolean enabled, BigDecimal regularPrice,
                   BigDecimal salePrice, String description, Category category) {
        this.setId(IdGenerator.generateTimeBasedUUID());
        this.setName(name);
        this.setBrand(brand);
        this.setDescription(description);
        this.setEnabled(enabled);
        this.setRegularPrice(regularPrice);
        this.setSalePrice(salePrice);
        this.setCategory(category);
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

    public void setRegularPrice(BigDecimal regularPrice) {
        Objects.requireNonNull(regularPrice);

        // se o numero for negativo
        if (regularPrice.signum() == -1) {
            throw new IllegalArgumentException();
        }

        if (this.salePrice == null) {
            this.salePrice = regularPrice;
        } else if (regularPrice.compareTo(this.salePrice) < 0) {
            throw new DomainException("Sale price cannot be greater than regular price");
        }

        this.regularPrice = regularPrice;
        this.calculateDiscountPercentage();
    }

    public void setSalePrice(BigDecimal salePrice) {
        Objects.requireNonNull(regularPrice);

        if (salePrice.signum() == -1) {
            throw new IllegalArgumentException();
        }

        if (this.regularPrice == null) {
            this.regularPrice = salePrice;
        } else if (this.regularPrice.compareTo(salePrice) < 0) {
            throw new DomainException("Sale price cannot be greater than regular price");
        }

        this.salePrice = salePrice;
        this.calculateDiscountPercentage();
    }

    public void setEnabled(Boolean enabled) {
        Objects.requireNonNull(enabled);
        this.enabled = enabled;
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

    public void setCategory(Category category) {
        Objects.requireNonNull(category);
        this.category = category;
    }

    public boolean isInStock() {
        return this.getQuantityInStock() != null && this.getQuantityInStock() > 0;
    }

    public boolean getHasDiscount() {
        return getDiscountPercentageRounded() != null && getDiscountPercentageRounded() > 0;
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
}
