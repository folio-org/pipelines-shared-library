package org.folio.utilities

class RequestException extends RuntimeException {
  final int statusCode
  final Object responseBody

  RequestException(String message, int statusCode, Object responseBody = null) {
    super(message)
    this.statusCode = statusCode
    this.responseBody = responseBody
  }
}

