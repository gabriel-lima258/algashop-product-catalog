package com.algaworks.algashop.product.catalog.presentation;

import com.algaworks.algashop.product.catalog.application.ResourceNotFoundException;
import com.algaworks.algashop.product.catalog.domain.DomainEntityNotFoundException;
import com.algaworks.algashop.product.catalog.domain.DomainException;
import com.algaworks.algashop.product.catalog.infrastructure.storage.StorageProviderException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private final MessageSource messageSource;

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle("Invalid fields");
        problemDetail.setDetail("One or more fields are invalid");
        problemDetail.setType(URI.create("/errors/invalid-fields"));

        Map<String, String> fieldErrors = ex.getBindingResult().getAllErrors().stream().collect(
                Collectors.toMap(
                        objectError -> ((FieldError) objectError).getField(),
                        this::messageOf
                )
        );

        problemDetail.setProperty("fields", fieldErrors);

        return super.handleExceptionInternal(ex, problemDetail,  headers, status, request);
    }

    // Dois tipos de erro chegam aqui e sao resolvidos de formas diferentes:
    //
    // - CONVERSAO (o texto da query string nao vira o tipo do campo): a mensagem do
    //   framework vaza nome de classe e pacote Java na resposta publica. A causa raiz,
    //   por outro lado, e a excecao do proprio conversor, que ja diz o que era esperado -
    //   e monta a lista a partir do enum, sem duplicar valores em lugar nenhum.
    // - VALIDACAO (Bean Validation): segue pelo MessageSource, como sempre.
    private String messageOf(ObjectError objectError) {
        if (objectError.contains(TypeMismatchException.class)) {
            return objectError.unwrap(TypeMismatchException.class).getMostSpecificCause().getMessage();
        }
        return messageSource.getMessage(objectError, LocaleContextHolder.getLocale());
    }

    // quando temos duas exception usando Exception
    @ExceptionHandler({DomainEntityNotFoundException.class, ResourceNotFoundException.class})
    public ProblemDetail handleNotFoundException(Exception e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setTitle("Not found");
        problemDetail.setDetail(e.getMessage());
        problemDetail.setType(URI.create("/errors/not-found"));
        return problemDetail;
    }

    // A AuthorizationDeniedException do @PreAuthorize estoura dentro do metodo do
    // controller, entao ela nao volta para o ExceptionTranslationFilter do Spring
    // Security - sem este handler, o catch-all de Exception a transformaria em 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problemDetail.setTitle("Forbidden");
        problemDetail.setDetail("You do not have permission to access this resource");
        problemDetail.setType(URI.create("/errors/forbidden"));
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception e) {
        log.error(e.getMessage(), e);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle("Internal server error");
        problemDetail.setDetail("An unexpected error occurred");
        problemDetail.setType(URI.create("/errors/internal"));
        return problemDetail;
    }

    @ExceptionHandler({DomainException.class, UnprocessableContentException.class, StorageProviderException.class})
    public ProblemDetail handleUnprocessableContentException(Exception e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        problemDetail.setTitle("Unprocessable content");
        problemDetail.setDetail(e.getMessage());
        problemDetail.setType(URI.create("/errors/unprocessable-content"));
        return problemDetail;
    }
}
