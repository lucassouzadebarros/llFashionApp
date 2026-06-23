package br.com.llfashion.whatsappcheckout.exception;

import br.com.llfashion.whatsappcheckout.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));

        return build(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "JSON da requisição inválido ou mal formatado", request.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        String message = "Método HTTP não suportado para este endpoint: " + exception.getMethod();
        return build(HttpStatus.METHOD_NOT_ALLOWED, message, request.getRequestURI());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException exception, HttpServletRequest request) {
        return build(exception.getStatus(), exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(NuvemshopApiException.class)
    public ResponseEntity<ErrorResponse> handleNuvemshopApi(NuvemshopApiException exception, HttpServletRequest request) {
        log.warn("Erro na API da Nuvemshop. status={}, contentType={}, message={}, bodyPreview={}",
                exception.getStatusCode(),
                exception.getContentType(),
                exception.getMessage(),
                exception.getResponseBody());

        HttpStatusCode statusCode = resolveExternalStatus(exception.getStatusCode());
        return build(statusCode, exception.getMessage(), request.getRequestURI(), exception.getDetails());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception exception, HttpServletRequest request) {
        log.error("Erro inesperado na aplicação", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado", request.getRequestURI());
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private HttpStatusCode resolveExternalStatus(int statusCode) {
        if (statusCode >= 400 && statusCode < 500) {
            return HttpStatusCode.valueOf(statusCode);
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private ResponseEntity<ErrorResponse> build(HttpStatusCode statusCode, String message, String path) {
        return build(statusCode, message, path, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatusCode statusCode, String message, String path, String details) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                statusCode.value(),
                reasonPhrase(statusCode),
                message,
                path,
                details
        );
        return ResponseEntity.status(statusCode).body(response);
    }

    private String reasonPhrase(HttpStatusCode statusCode) {
        if (statusCode instanceof HttpStatus httpStatus) {
            return httpStatus.getReasonPhrase();
        }
        return "HTTP " + statusCode.value();
    }
}
