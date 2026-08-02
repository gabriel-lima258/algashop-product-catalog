package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.domain.DomainException;
import com.algaworks.algashop.product.catalog.domain.category.Category;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

// Teste do AGREGADO, sem Spring: nao ha contexto, banco nem mock. O Product e um objeto
// Java comum, e as regras que ele guarda podem ser exercitadas assim - se este teste
// precisasse de infraestrutura, seria sinal de que a regra vazou do dominio.
//
// A unica concessao e o eventsOf() la embaixo: os eventos enfileirados so sao expostos
// por domainEvents(), protegido no AbstractAggregateRoot. Ver o comentario do metodo.
class ProductTest {

    @Test
    void shouldAcceptLoweringBothPricesTogether() {
        Product product = aProduct();

        // 2500 e menor que o salePrice ANTIGO (2789), mas maior que o novo (2400).
        // a comparacao com o preco antigo rejeitava esta troca perfeitamente valida
        assertThatCode(() -> product.changePrice(new BigDecimal("2500"), new BigDecimal("2400")))
                .doesNotThrowAnyException();

        assertThat(product.getRegularPrice()).isEqualByComparingTo("2500");
        assertThat(product.getSalePrice()).isEqualByComparingTo("2400");
    }

    @Test
    void shouldRejectSalePriceGreaterThanRegularPriceOnChange() {
        Product product = aProduct();

        // 3000 nao e menor que o salePrice antigo (2789), entao a comparacao errada
        // deixava passar - e o agregado terminava com promocao mais cara que o preco cheio
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> product.changePrice(new BigDecimal("3000"), new BigDecimal("5000")));

        // e o estado anterior continua intacto: a validacao roda ANTES de aplicar
        assertThat(product.getRegularPrice()).isEqualByComparingTo("3000");
        assertThat(product.getSalePrice()).isEqualByComparingTo("2789");
    }

    @Test
    void shouldRejectSalePriceGreaterThanRegularPriceOnCreation() {
        // o caminho da criacao tambem precisa validar: os setters de preco ficaram
        // privados e nao guardam mais a regra do par
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> Product.builder()
                        .name("Notebook X11")
                        .brand("Deep Diver")
                        .enabled(true)
                        .regularPrice(new BigDecimal("100"))
                        .salePrice(new BigDecimal("999"))
                        .category(aCategory())
                        .build());
    }

    @Test
    void shouldRegisterProductAddedEventOnCreation() {
        Product product = aProduct();

        assertThat(eventsOf(product)).hasExactlyElementsOfTypes(ProductAddedEvent.class);
    }

    @Test
    void shouldRegisterPriceChangedAndPlacedOnSaleWhenDiscountAppears() {
        // nasce sem desconto: os dois precos iguais
        Product product = Product.builder()
                .name("Notebook X11")
                .brand("Deep Diver")
                .enabled(true)
                .regularPrice(new BigDecimal("3000"))
                .salePrice(new BigDecimal("3000"))
                .category(aCategory())
                .build();

        product.changePrice(new BigDecimal("3000"), new BigDecimal("2500"));

        // dois eventos para uma mudanca: o fato bruto e a promocao que nasceu dele
        assertThat(eventsOf(product)).hasExactlyElementsOfTypes(
                ProductAddedEvent.class,
                ProductPriceChangedEvent.class,
                ProductPlacedOnSaleEvent.class);
    }

    @Test
    void shouldNotRegisterAnyEventWhenPricesDoNotChange() {
        Product product = aProduct();
        int eventsBefore = eventsOf(product).size();

        product.changePrice(new BigDecimal("3000"), new BigDecimal("2789"));

        assertThat(eventsOf(product)).hasSize(eventsBefore);
    }

    @Test
    void shouldRegisterDelistedEventWhenDisablingAnEnabledProduct() {
        Product product = aProduct();

        product.disable();

        assertThat(eventsOf(product)).hasExactlyElementsOfTypes(
                ProductAddedEvent.class,
                ProductDelistedEvent.class);
    }

    @Test
    void shouldNotRegisterEventWhenDisablingAnAlreadyDisabledProduct() {
        Product product = aProduct();
        product.disable();
        int eventsBefore = eventsOf(product).size();

        // repetir a operacao nao e um fato novo
        product.disable();

        assertThat(eventsOf(product)).hasSize(eventsBefore);
    }

    @Test
    void shouldCopyCategoryDataIntoTheProductDocument() {
        Product product = aProduct();

        // a desnormalizacao em uma linha: o produto guarda a COPIA, nao a Category
        assertThat(product.getCategory()).isInstanceOf(ProductCategory.class);
        assertThat(product.getCategory().getName()).isEqualTo("Laptops");
        assertThat(product.getCategory().getEnabled()).isTrue();
    }

    private Product aProduct() {
        return Product.builder()
                .name("HyperNova Pro X11")
                .brand("QuantumTech")
                .description("15-inch laptop")
                .enabled(true)
                .regularPrice(new BigDecimal("3000"))
                .salePrice(new BigDecimal("2789"))
                .category(aCategory())
                .build();
    }

    private Category aCategory() {
        return new Category("Laptops", true);
    }

    // domainEvents() e protected no AbstractAggregateRoot, que vive em outro pacote -
    // e protected entre pacotes exige ser SUBCLASSE, nao apenas vizinho. Estar no mesmo
    // pacote do Product nao ajuda, entao o acesso vai por reflexao.
    // Invoca pelo NOME DO METODO, e nao pelo campo: domainEvents() e a API que o Spring
    // Data documenta e o proprio framework chama; o campo por tras dela e detalhe interno
    // que pode ser renomeado sem aviso
    @SuppressWarnings("unchecked")
    private Collection<Object> eventsOf(Product product) {
        return (Collection<Object>) ReflectionTestUtils.invokeMethod(product, "domainEvents");
    }
}
