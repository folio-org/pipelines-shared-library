package org.folio.utilities

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.json.internal.LazyMap

class RestClient {
  private boolean debug
  private int defaultConnectionTimeout
  private int defaultReadTimeout
  private Logger logger

  RestClient(Object context, boolean debug = false, int defaultConnectionTimeout = 120000, int defaultReadTimeout = 10800000) {
    this.debug = debug
    this.defaultConnectionTimeout = defaultConnectionTimeout
    this.defaultReadTimeout = defaultReadTimeout
    this.logger = new Logger(context, 'RestClient')
  }

  def get(String url, Map<String, String> headers = [:], List<Integer> validResponseCodes = []
          , int connectionTimeout = defaultConnectionTimeout, int readTimeout = defaultReadTimeout) {
    return doRequest('GET', url, null, headers, validResponseCodes, connectionTimeout, readTimeout)
  }

  def post(String url, Object body, Map<String, String> headers = [:], List<Integer> validResponseCodes = []
           , int connectionTimeout = defaultConnectionTimeout, int defaultReadTimeout = defaultReadTimeout) {
    return doRequest('POST', url, body, headers, validResponseCodes, connectionTimeout, defaultReadTimeout)
  }

  def delete(String url, Map<String, String> headers = [:], List<Integer> validResponseCodes = []
             , int connectionTimeout = defaultConnectionTimeout, int readTimeout = defaultReadTimeout) {
    return doRequest('DELETE', url, null, headers, validResponseCodes, connectionTimeout, readTimeout)
  }

  @NonCPS
  def put(String url, Object body, Map<String, String> headers = [:], List<Integer> validResponseCodes = []
          , int connectionTimeout = defaultConnectionTimeout, int readTimeout = defaultReadTimeout) {
    return doRequest('PUT', url, body, headers, validResponseCodes, connectionTimeout, readTimeout)
  }

  def upload(String url, File file, Map<String, String> headers = [:], List<Integer> validResponseCodes = []
             , int connectionTimeout = defaultConnectionTimeout, int readTimeout = defaultReadTimeout) {
    headers['Content-Type'] = 'application/octet-stream'
    return doRequest('POST', url, file.bytes, headers, validResponseCodes, connectionTimeout, readTimeout)
  }

  private def doRequest(String method, String url, Object body, Map<String, String> headers
                        , List<Integer> validResponseCodes = []
                        , int connectionTimeout, int readTimeout) {

    if (debug) {
      logger.debug("[HTTP REQUEST]: method=${method}, url=${url}, headers=${headers}, body=${body}")
    }

    HttpURLConnection connection = setupConnection(url, method, headers, connectionTimeout, readTimeout)

    if (body) {
      sendRequestBody(connection, body)
    }

    Map response = [:]
    try {
      response = parseResponse(connection)
    } catch (e) {
      logger.error(e.getMessage())
    }

    if (debug) {
      logger.debug("[HTTP RESPONSE]: status=${connection.responseCode}, headers=${response.headers}, body=${response.body}")
    }

    if (!validResponseCodes?.contains(connection.responseCode) && connection.responseCode >= 400){
      handleHttpError(connection.responseCode, connection.responseMessage, response.body.toString())
    }

    return response

  }

  private HttpURLConnection setupConnection(String url, String method, Map<String, String> headers, int connectionTimeout, int readTimeout) {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection()
    connection.requestMethod = method
    connection.connectTimeout = connectionTimeout
    connection.readTimeout = readTimeout
    headers.each { header, value ->
      connection.setRequestProperty(header, value)
    }
    return connection
  }

  private void sendRequestBody(HttpURLConnection connection, Object body) {
    if (body instanceof Map || body instanceof List) {
      body = JsonOutput.toJson(body)
      if (!connection.getRequestProperty('Content-Type')) {
        connection.setRequestProperty('Content-Type', 'application/json')
      }
    } else if (body instanceof byte[]) {
      connection.setRequestProperty('Content-Type', 'application/octet-stream')
    } else {
      body = body.toString()
    }
    connection.doOutput = true
    connection.outputStream.write(body.bytes)
  }

  private def parseResponse(HttpURLConnection connection) {
    int responseCode = connection.responseCode
    InputStream inputStream = (responseCode >= 200 && responseCode < 300) ?
      connection.inputStream : connection.errorStream

    String responseBody = inputStream.text

    if (responseBody == null || responseBody.trim().isEmpty()) {
      return [body: null, headers: connection.getHeaderFields(), responseCode: responseCode]
    } else {
      String contentType = connection.getHeaderField("Content-Type")
      if (contentType != null && contentType.contains("application/json")) {
        def parsedResponse = new JsonSlurper().parseText(responseBody)
        if (parsedResponse instanceof LazyMap) {
          parsedResponse = new HashMap(parsedResponse)
        } else if (parsedResponse instanceof List && !parsedResponse.isEmpty() && parsedResponse[0] instanceof LazyMap) {
          parsedResponse = parsedResponse.collect { new HashMap(it) }
        }
        return [body: parsedResponse, headers: connection.getHeaderFields(), responseCode: responseCode]
      } else {
        return [body: responseBody, headers: connection.getHeaderFields(), responseCode: responseCode]
      }
    }
  }

  private static void handleHttpError(int statusCode, String statusMessage, String responseBody) {
    throw new RequestException("${statusMessage}(${statusCode}) - ${responseBody}", statusCode)
  }
}
