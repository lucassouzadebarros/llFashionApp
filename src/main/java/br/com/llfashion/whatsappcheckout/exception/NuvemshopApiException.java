package br.com.llfashion.whatsappcheckout.exception;

public class NuvemshopApiException extends RuntimeException {

    private final int statusCode;
    private final String contentType;
    private final String responseBody;

    public NuvemshopApiException(int statusCode, String message, String responseBody) {
        this(statusCode, message, null, responseBody, null);
    }

    public NuvemshopApiException(int statusCode, String message, String contentType, String responseBody) {
        this(statusCode, message, contentType, responseBody, null);
    }

    public NuvemshopApiException(int statusCode, String message, String responseBody, Throwable cause) {
        this(statusCode, message, null, responseBody, cause);
    }

    public NuvemshopApiException(int statusCode, String message, String contentType, String responseBody, Throwable cause) {
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

    public String getDetails() {
        StringBuilder details = new StringBuilder();
        details.append("A Nuvemshop retornou status ").append(statusCode);
        if (contentType != null) {
            details.append(" e Content-Type ").append(contentType);
        }
        if (responseBody != null) {
            details.append(". Body preview: ").append(responseBody);
        }
        return details.toString();
    }
}
