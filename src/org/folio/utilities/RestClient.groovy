package org.folio.utilities

import groovy.json.internal.LazyMap

/**
 * A Jenkins-friendly REST client that executes HTTP requests using `curl`.
 * This class supports multiple HTTP verbs (GET, POST, PUT, DELETE, and file upload)
 * and provides convenient handling of request bodies (JSON, byte[] for binary, and text),
 * custom headers, response code validation, and debug logging.
 *
 * Usage:
 * <pre>
 *   def restClient = new RestClient(this, debug: true)
 *   def response = restClient.get('https://example.com/api', [Accept: 'application/json'])
 *   echo "Response code: ${response.responseCode}"
 *   echo "Response body: ${response.body}"
 * </pre>
 */
class RestClient {

  /**
   * The Jenkins pipeline context (usually `this` in a Pipeline script),
   * needed to call pipeline steps such as `sh`, `writeFile`, `readJSON`.
   */
  private Object steps

  /**
   * Flag to enable or disable debug logging.
   * If set to `true`, debug statements will be logged.
   */
  private boolean debug

  /**
   * The default connection timeout (in milliseconds) for requests.
   */
  private int defaultConnectionTimeout

  /**
   * The default read timeout (in milliseconds) for requests.
   */
  private int defaultReadTimeout

  /**
   * A Logger instance for structured logging of requests and responses.
   */
  private Logger logger

  /**
   * Constructs a new RestClient.
   *
   * @param context                  A reference to the Jenkins pipeline `this` context.
   * @param debug                    Enables debug logging if true (default: false).
   * @param defaultConnectionTimeout Connection timeout in milliseconds (default: 120000 ms).
   * @param defaultReadTimeout       Read timeout in milliseconds (default: 10800000 ms).
   */
  RestClient(Object context,
             boolean debug = false,
             int defaultConnectionTimeout = 120000,
             int defaultReadTimeout = 10800000) {
    this.steps = context
    this.debug = debug
    this.defaultConnectionTimeout = defaultConnectionTimeout
    this.defaultReadTimeout = defaultReadTimeout
    this.logger = new Logger(context, 'RestClient')
  }

  /**
   * Sends an HTTP GET request.
   *
   * @param url                 The target URL.
   * @param headers             A map of HTTP headers. Defaults to an empty map.
   * @param validResponseCodes  List of HTTP status codes to treat as successful
   *                            even if >= 400 (default: empty list).
   * @param connectionTimeout   Connection timeout in ms (defaults to `defaultConnectionTimeout`).
   * @param readTimeout         Read timeout in ms (defaults to `defaultReadTimeout`).
   * @return A Map containing:
   *         <ul>
   *           <li>body: Parsed JSON (Map or List) or String if not JSON</li>
   *           <li>headers: Map of header names to List of header values</li>
   *           <li>responseCode: The HTTP status code</li>
   *         </ul>
   */
  Map get(String url,
          Map<String, String> headers = [:],
          List<Integer> validResponseCodes = [],
          int connectionTimeout = defaultConnectionTimeout,
          int readTimeout = defaultReadTimeout) {
    return doRequest('GET', url, null, headers, validResponseCodes, connectionTimeout, readTimeout)
  }

  /**
   * Sends an HTTP POST request with a request body.
   *
   * @param url                 The target URL.
   * @param body                The request body. Can be a Map/List (serialized to JSON),
   *                            a byte[] (treated as binary), or any other object (converted to String).
   * @param headers             A map of HTTP headers. Defaults to an empty map.
   * @param validResponseCodes  List of HTTP status codes to treat as successful
   *                            even if >= 400 (default: empty list).
   * @param connectionTimeout   Connection timeout in ms (defaults to `defaultConnectionTimeout`).
   * @param readTimeout         Read timeout in ms (defaults to `defaultReadTimeout`).
   * @return A Map containing the same keys as `get()`—body, headers, and responseCode.
   */
  Map post(String url,
           Object body,
           Map<String, String> headers = [:],
           List<Integer> validResponseCodes = [],
           int connectionTimeout = defaultConnectionTimeout,
           int readTimeout = defaultReadTimeout) {
    return doRequest('POST', url, body, headers, validResponseCodes, connectionTimeout, readTimeout)
  }

  /**
   * Sends an HTTP DELETE request.
   *
   * @param url                 The target URL.
   * @param headers             A map of HTTP headers. Defaults to an empty map.
   * @param validResponseCodes  List of HTTP status codes to treat as successful
   *                            even if >= 400 (default: empty list).
   * @param connectionTimeout   Connection timeout in ms (defaults to `defaultConnectionTimeout`).
   * @param readTimeout         Read timeout in ms (defaults to `defaultReadTimeout`).
   * @return A Map containing body, headers, and responseCode.
   */
  Map delete(String url,
             Map<String, String> headers = [:],
             List<Integer> validResponseCodes = [],
             int connectionTimeout = defaultConnectionTimeout,
             int readTimeout = defaultReadTimeout) {
    return doRequest('DELETE', url, null, headers, validResponseCodes, connectionTimeout, readTimeout)
  }

  /**
   * Sends an HTTP PUT request with a request body.
   *
   * @param url                 The target URL.
   * @param body                The request body (Map/List -> JSON, byte[] -> binary, String -> text).
   * @param headers             A map of HTTP headers. Defaults to an empty map.
   * @param validResponseCodes  List of HTTP status codes to treat as successful
   *                            even if >= 400 (default: empty list).
   * @param connectionTimeout   Connection timeout in ms (defaults to `defaultConnectionTimeout`).
   * @param readTimeout         Read timeout in ms (defaults to `defaultReadTimeout`).
   * @return A Map containing body, headers, and responseCode.
   */
  Map put(String url,
          Object body,
          Map<String, String> headers = [:],
          List<Integer> validResponseCodes = [],
          int connectionTimeout = defaultConnectionTimeout,
          int readTimeout = defaultReadTimeout) {
    return doRequest('PUT', url, body, headers, validResponseCodes, connectionTimeout, readTimeout)
  }

  /**
   * Uploads a file as a binary payload via a POST request.
   * Internally sets the `Content-Type` header to `application/octet-stream`.
   *
   * @param url                 The target URL.
   * @param file                The file to upload.
   * @param headers             A map of HTTP headers. Defaults to an empty map.
   * @param validResponseCodes  List of HTTP status codes to treat as successful
   *                            even if >= 400 (default: empty list).
   * @param connectionTimeout   Connection timeout in ms (defaults to `defaultConnectionTimeout`).
   * @param readTimeout         Read timeout in ms (defaults to `defaultReadTimeout`).
   * @return A Map containing body, headers, and responseCode.
   */
  Map upload(String url,
             File file,
             Map<String, String> headers = [:],
             List<Integer> validResponseCodes = [],
             int connectionTimeout = defaultConnectionTimeout,
             int readTimeout = defaultReadTimeout) {
    headers['Content-Type'] = 'application/octet-stream'
    return doRequest('POST', url, file.bytes, headers, validResponseCodes, connectionTimeout, readTimeout)
  }

  /**
   * Core method that executes the HTTP request by constructing and running a curl command.
   * Each public method (get, post, put, delete, upload) eventually calls this method.
   *
   * @param method              The HTTP method (GET, POST, PUT, DELETE).
   * @param url                 The target URL.
   * @param body                Request body (any type: Map, List, byte[], String, null).
   * @param headers             A map of HTTP headers.
   * @param validResponseCodes  List of status codes considered successful if >= 400.
   * @param connectionTimeout   Connection timeout in ms.
   * @param readTimeout         Read timeout in ms.
   * @return A map with the keys:
   *         <ul>
   *           <li>body: Parsed JSON or raw String response</li>
   *           <li>headers: Map of header names to List of header values</li>
   *           <li>responseCode: The HTTP status code</li>
   *         </ul>
   */
  private Map doRequest(String method,
                        String url,
                        Object body,
                        Map<String, String> headers,
                        List<Integer> validResponseCodes,
                        int connectionTimeout,
                        int readTimeout) {

    String bodyFilePath = null

    try {
      if (debug) {
        logger.debug("[HTTP REQUEST] method=${method}, url=${url}, headers=${headers}, body=${body}")
      }

      // Prepare the request body: write to a temporary file if needed
      bodyFilePath = writeRequestBodyToFile(body, headers)

      // Convert timeouts from milliseconds to seconds for the curl command
      int connectSeconds = Math.round(connectionTimeout / 1000)
      int readSeconds = Math.round(readTimeout / 1000)

      // Build the curl command string
      String curlCmd = buildCurlCommand(method, url, headers, bodyFilePath, connectSeconds, readSeconds, body)
      if (debug) {
        logger.debug("[CURL COMMAND] ${curlCmd}")
      }

      // Execute the curl command, capturing stdout
      String curlOutput = steps.sh(script: curlCmd, returnStdout: true).trim()

      // Parse the combined headers, body, and HTTP status code from the curl output
      Map parsed = parseCurlOutput(curlOutput)

      if (debug) {
        logger.debug("[HTTP RESPONSE] status=${parsed.responseCode}, headers=${parsed.headers}, body=${parsed.body}")
      }

      // If the status code is >= 400 and not in validResponseCodes, throw an error
      if (!validResponseCodes?.contains(parsed.responseCode) && parsed.responseCode >= 400) {
        handleHttpError(parsed.responseCode, "HTTP request failed", parsed.body?.toString())
      }

      return parsed

    } finally {
      // Cleanup: remove the temporary body file if it was created
      if (bodyFilePath) {
        steps.sh(script: """
          set +x
          rm -f '${bodyFilePath}'
          set -x
        """)
      }
    }
  }

  /**
   * Writes the request body to a temporary file (if not null) and returns its path.
   * Automatically sets the Content-Type header if not provided.
   *
   * @param body    The request body object, which can be:
   *                <ul>
   *                  <li>Map/List: converted to JSON</li>
   *                  <li>byte[]: stored as base64 in the file</li>
   *                  <li>String: stored as plain text</li>
   *                  <li>null: no file is written</li>
   *                </ul>
   * @param headers A map of headers; may be updated with a default Content-Type if absent.
   * @return The path to the temporary file, or null if no file was needed.
   */
  private String writeRequestBodyToFile(Object body, Map<String, String> headers) {
    if (body == null) {
      return null
    }

    // Only set Content-Type if not already provided
    if (!headers['Content-Type']) {
      if (body instanceof Map || body instanceof List) {
        headers['Content-Type'] = 'application/json'
      } else if (body instanceof byte[]) {
        headers['Content-Type'] = 'application/octet-stream'
      } else {
        headers['Content-Type'] = 'text/plain'
      }
    }

    // Create a unique temp filename with a random UUID to avoid collisions in parallel steps
    String fileName = "restClientBody_${System.currentTimeMillis()}_${UUID.randomUUID().toString()}.tmp"

    if (body instanceof Map || body instanceof List) {
      // Convert to JSON string
      String jsonStr = steps.writeJSON(returnText: true, json: body)
      steps.writeFile file: fileName, text: jsonStr, encoding: 'UTF-8'
    } else if (body instanceof byte[]) {
      // Write binary data as base64 so we can decode it in the shell
      String base64data = body.encodeBase64().toString()
      steps.writeFile file: fileName, text: base64data, encoding: 'UTF-8'
    } else {
      // Treat as a string
      steps.writeFile file: fileName, text: body.toString(), encoding: 'UTF-8'
    }

    return fileName
  }

  /**
   * Builds the curl command string to be executed in the shell.
   * Depending on whether the request body is a byte array, it may prepend a base64 decode command.
   *
   * @param method            The HTTP method (GET, POST, PUT, DELETE).
   * @param url               The target URL.
   * @param headers           A map of HTTP headers.
   * @param bodyFilePath      The path to the temporary file containing the request body; may be null.
   * @param connectTimeoutSec Connection timeout (seconds).
   * @param readTimeoutSec    Read timeout (seconds).
   * @param originalBody      The original (unmodified) body object, used to check if it's a byte[].
   * @return The fully constructed shell command string.
   */
  private String buildCurlCommand(String method,
                                  String url,
                                  Map<String, String> headers,
                                  String bodyFilePath,
                                  int connectTimeoutSec,
                                  int readTimeoutSec,
                                  Object originalBody) {
    // Start building the curl command
    StringBuilder cmd = new StringBuilder("curl -s -S -X ${method}")

    // Include each header; escape single quotes to prevent shell injection
    headers.each { k, v ->
      String safeValue = v ?: ""
      cmd.append(" -H '${escapeSingleQuotes(k)}: ${escapeSingleQuotes(safeValue)}'")
    }

    // Set connection and read timeouts in seconds
    cmd.append(" --connect-timeout ${connectTimeoutSec}")
    cmd.append(" --max-time ${readTimeoutSec}")
    // Capture response headers in stdout (`-D -`)
    cmd.append(" -D -")

    // If we have a file with the request body, attach it properly
    if (bodyFilePath) {
      if (originalBody instanceof byte[]) {
        // For binary data, decode from base64, then pipe into curl as binary
        cmd.insert(0, "base64 --decode ${bodyFilePath} | ") // prepend the decode
        cmd.append(" --data-binary @-")
      } else {
        // For JSON or text, just pass the file content directly
        cmd.append(" --data-binary @${escapeSingleQuotes(bodyFilePath)}")
      }
    }

    // Add the URL, escaping single quotes
    cmd.append(" '${escapeSingleQuotes(url)}'")

    // Print the HTTP status code at the end for easy parsing
    cmd.append(" -w '\\nHTTP_CODE:%{http_code}'")

    // Wrap the entire command with set +x / set -x
    String wrapped = """
      set +x
      ${cmd.toString()}
      set -x
    """

    return wrapped
  }

  /**
   * Parses the output of curl, which includes headers, body, and a final line containing HTTP_CODE.
   *
   * @param curlOutput The raw string output from the curl command.
   * @return A Map containing:
   *         <ul>
   *           <li>body: The parsed JSON (if Content-Type is application/json) or raw string</li>
   *           <li>headers: A Map of header keys to a List of string values</li>
   *           <li>responseCode: The integer HTTP status code</li>
   *         </ul>
   */
  private Map parseCurlOutput(String curlOutput) {
    // Split by newline
    List<String> lines = curlOutput.split('\n')
    if (!lines) {
      // No output
      return [body: null, headers: [:], responseCode: 0]
    }

    // The last line is expected to be "HTTP_CODE:<status>"
    String lastLine = lines[-1]
    int responseCode = 0
    if (lastLine.startsWith('HTTP_CODE:')) {
      responseCode = lastLine.replace('HTTP_CODE:', '').trim().toInteger()
      lines.remove(lines.size() - 1)
    }

    // Separate headers from body by a blank line or a possible fallback location
    Map<String, List<String>> headersMap = [:]
    int blankLineIndex = lines.indexOf('')
    if (blankLineIndex == -1) {
      blankLineIndex = findHeaderBodySplit(lines)
    }

    // Everything from the start up to the blank line is headers
    List<String> headerLines = (blankLineIndex != -1) ? lines[0..<(blankLineIndex)] : lines
    // Everything after that line is the body
    List<String> bodyLines = (blankLineIndex != -1 && blankLineIndex < lines.size() - 1)
      ? lines[(blankLineIndex + 1)..<lines.size()]
      : []

    // Build a map of headers: Some headers can appear multiple times
    headerLines.each { line ->
      if (line.contains(':')) {
        def (key, value) = line.split(':', 2)
        key = key?.trim()
        value = value?.trim()
        if (!headersMap.containsKey(key)) {
          headersMap[key] = []
        }
        headersMap[key] << value
      }
    }

    // Combine the body lines
    String responseBody = bodyLines.join('\n')
    if (!responseBody?.trim()) {
      // Empty body
      return [body: null, headers: headersMap, responseCode: responseCode]
    }

    // Check if the content type is JSON
    String contentTypeLine = (headersMap.find { k, v -> k.equalsIgnoreCase('Content-Type') })?.value?.join(';')
    if (contentTypeLine?.contains('application/json')) {
      // Parse JSON using Jenkins pipeline step `readJSON`
      def parsedResponse = steps.readJSON(text: responseBody, returnPojo: true)
      // Convert LazyMap(s) to HashMap(s) for easier usage
      if (parsedResponse instanceof LazyMap) {
        parsedResponse = new HashMap(parsedResponse)
      } else if (parsedResponse instanceof List && !parsedResponse.isEmpty() && parsedResponse[0] instanceof LazyMap) {
        parsedResponse = parsedResponse.collect { new HashMap(it) }
      }
      return [body: parsedResponse, headers: headersMap, responseCode: responseCode]
    } else {
      // Return raw text if not JSON
      return [body: responseBody, headers: headersMap, responseCode: responseCode]
    }
  }

  /**
   * Finds the index where headers end and the body begins, attempting to detect lines
   * that are not header lines (i.e., do not contain a colon and are not HTTP/1.x status lines).
   *
   * @param lines The lines of the curl output.
   * @return The index of the first body line, or -1 if no clear split is found.
   */
  private int findHeaderBodySplit(List<String> lines) {
    for (int i = 0; i < lines.size(); i++) {
      String line = lines[i]
      // If the line doesn't contain a colon or is not an HTTP status line, assume it's part of the body.
      if (!line.contains(':') && !line.toUpperCase().startsWith('HTTP/')) {
        return i
      }
    }
    return -1
  }

  /**
   * Escapes single quotes in a string for safe inclusion in shell commands.
   *
   * @param input The string to be escaped.
   * @return A shell-safe version of the string with single quotes escaped.
   */
  private static String escapeSingleQuotes(String input) {
    if (!input) {
      return ""
    }
    // Replace any single quote ' with '"'"' to close and reopen quoting in shell
    return input.replace("'", "'\"'\"'")
  }

  /**
   * Throws a RequestException for an HTTP error status code.
   *
   * @param statusCode    The numeric HTTP status code (e.g., 404).
   * @param statusMessage A short message describing the error context.
   * @param responseBody  The body from the server response, if available.
   */
  private static void handleHttpError(int statusCode, String statusMessage, String responseBody) {
    throw new RequestException("${statusMessage}(${statusCode}) - ${responseBody}", statusCode)
  }
}
