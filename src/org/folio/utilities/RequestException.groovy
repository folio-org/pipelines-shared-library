package org.folio.utilities

class RequestException extends RuntimeException {
    int statusCode

    RequestException(String message, int statusCode) {
        super(message)
        this.statusCode = statusCode
    }
}
