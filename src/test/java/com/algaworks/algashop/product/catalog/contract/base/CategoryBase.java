package com.algaworks.algashop.product.catalog.contract.base;

import com.algaworks.algashop.product.catalog.application.util.PageModel;
import com.algaworks.algashop.product.catalog.application.ResourceNotFoundException;
import com.algaworks.algashop.product.catalog.application.category.management.CategoryInput;
import com.algaworks.algashop.product.catalog.application.category.management.CategoryManagementApplicationService;
import com.algaworks.algashop.product.catalog.application.category.query.CategoryDetailOutput;
import com.algaworks.algashop.product.catalog.application.category.query.CategoryDetailOutputTestDataBuilder;
import com.algaworks.algashop.product.catalog.application.category.query.CategoryFilter;
import com.algaworks.algashop.product.catalog.application.category.query.CategoryQueryService;
import com.algaworks.algashop.product.catalog.presentation.CategoryController;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@WebMvcTest(controllers = CategoryController.class)
public class CategoryBase {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CategoryQueryService categoryQueryService;

    @MockitoBean
    private CategoryManagementApplicationService categoryManagementApplicationService;

    public static final UUID validCategoryId = UUID.fromString("fffe6ec2-7103-48b3-8e4f-3b58e43fb75a");
    public static final UUID validUpdateCategoryId = UUID.fromString("ccce6ec2-7103-48b3-8e4f-3b58e43fb75a");
    public static final UUID invalidUpdateCategoryId = UUID.fromString("c77e6ec2-7103-48b3-8e4f-3b58e43fb75a");
    public static final UUID invalidCategoryId = UUID.fromString("216e6ec2-7103-48b3-8e4f-3b58e43fb75a");
    public static final UUID createCategoryId = UUID.fromString("aaae6ec2-7103-48b3-8e4f-3b58e43fb75a");
    public static final UUID deleteCategoryId = UUID.fromString("f1d3a7c4-6b2e-4f8a-9217-5d9c2e1b3a5f");
    public static final UUID deleteInvalidCategoryId = UUID.fromString("177e6ec2-7103-48b3-8e4f-3b58e43fb75a");

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.webAppContextSetup(context)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8).build());

        RestAssuredMockMvc.enableLoggingOfRequestAndResponseIfValidationFails();

        mockFilterCategories();
        mockValidCategoryFindById();
        mockInvalidCategoryIdNotFound();
        mockCreateCategory();
        mockUpdateCategory();
        mockInvalidCategoryIdUpdated();
        mockDeleteProductById();
        mockInvalidDeletedProductId();
    }

    // o contrato manda ?size=10&number=0 e afirma que a resposta devolve o mesmo size,
    // entao o stub le o size do filtro recebido em vez de devolver valor fixo
    private void mockFilterCategories() {
        Mockito.when(categoryQueryService.filter(
                Mockito.any(CategoryFilter.class)
        )).then((answer) -> {
            CategoryFilter filter = answer.getArgument(0);

            return PageModel.<CategoryDetailOutput>builder()
                    .number(0)
                    .size(filter.getSize())
                    .totalPages(1)
                    .totalElements(2)
                    .content(
                            List.of(
                                    CategoryDetailOutputTestDataBuilder.aNotebookCategory().build(),
                                    CategoryDetailOutputTestDataBuilder.anElectronicCategory().build()
                            )
                    ).build();
        });
    }

    private void mockValidCategoryFindById() {
        Mockito.when(categoryQueryService.findById(validCategoryId))
                .thenReturn(CategoryDetailOutputTestDataBuilder.aNotebookCategory()
                        .id(validCategoryId)
                        .build());
    }

    private void mockInvalidCategoryIdNotFound() {
        Mockito.when(categoryQueryService.findById(invalidCategoryId))
                .thenThrow(new ResourceNotFoundException());
    }

    private void mockCreateCategory() {
        Mockito.when(categoryManagementApplicationService.create(Mockito.any(CategoryInput.class)))
                .thenReturn(createCategoryId);
        Mockito.when(categoryQueryService.findById(createCategoryId))
                .thenReturn(CategoryDetailOutputTestDataBuilder.aNotebookCategory().build());
    }

    private void mockUpdateCategory() {
        Mockito.doNothing().when(categoryManagementApplicationService).update(Mockito.any(UUID.class), Mockito.any(CategoryInput.class));
        Mockito.when(categoryQueryService.findById(validUpdateCategoryId))
                .thenReturn(CategoryDetailOutputTestDataBuilder.aNotebookCategory().build());
    }

    private void mockInvalidCategoryIdUpdated() {
        Mockito.doThrow(new ResourceNotFoundException())
                .when(categoryManagementApplicationService)
                .update(Mockito.eq(invalidUpdateCategoryId), Mockito.any(CategoryInput.class));
    }

    private void mockDeleteProductById() {
        Mockito.doNothing().when(categoryManagementApplicationService).disable(deleteCategoryId);
    }

    private void mockInvalidDeletedProductId() {
        Mockito.doThrow(new ResourceNotFoundException())
                .when(categoryManagementApplicationService)
                .disable(deleteInvalidCategoryId);
    }

}
