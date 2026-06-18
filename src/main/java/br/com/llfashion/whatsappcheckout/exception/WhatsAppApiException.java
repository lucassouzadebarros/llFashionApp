package br.com.llfashion.whatsappcheckout.exception;

public class WhatsAppApiException extends RuntimeException {

    private final int statusCode;
    private final String contentType;
    private final String responseBody;

    public WhatsAppApiException(int statusCode, String message, String contentType, String responseBody) {
        this(statusCode, message, contentType, responseBody, null);
    }

    public WhatsAppApiException(int statusCode, String message, String contentType, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getContentType() {
        return contentType;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
