package org.folio.models

/**
 * This class defines the parameters required for installation query.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class InstallRequestParams implements Cloneable {

  /** Asynchronous processing flag. */
  boolean async

  /** Flag indicating if errors should be ignored. */
  boolean ignoreErrors

  /** Flag indicating if reinstall is necessary. */
  boolean reinstall

  /** Simulation mode flag. */
  boolean simulate

  /** Additional tenant parameters for the install request. */
  String tenantParameters = ''

  /**
   * Chainable setter for the async flag.
   * @param async The new value for the async flag.
   * @return The updated InstallRequestParams object.
   */
  InstallRequestParams withAsync(boolean async) {
    this.async = async
    return this
  }

  /**
   * Chainable setter for the ignoreErrors flag.
   * @param ignoreErrors The new value for the ignoreErrors flag.
   * @return The updated InstallRequestParams object.
   */
  InstallRequestParams withIgnoreErrors(boolean ignoreErrors) {
    this.ignoreErrors = ignoreErrors
    return this
  }

  /**
   * Chainable setter for the reinstall flag.
   * @param reinstall The new value for the reinstall flag.
   * @return The updated InstallRequestParams object.
   */
  InstallRequestParams withReinstall(boolean reinstall) {
    this.reinstall = reinstall
    return this
  }

  /**
   * Chainable setter for the simulate flag.
   * @param simulate The new value for the simulate flag.
   * @return The updated InstallRequestParams object.
   */
  InstallRequestParams withSimulate(boolean simulate) {
    this.simulate = simulate
    return this
  }

  /**
   * Chainable setter for the tenantParameters string.
   * @param tenantParameters The new tenant parameters.
   * @return The updated InstallRequestParams object.
   */
  InstallRequestParams withTenantParameters(String tenantParameters) {
    this.tenantParameters = "tenantParameters=" + encode(tenantParameters)
    return this
  }

  /**
   * Adds a tenant parameter.
   *
   * @param key The parameter key.
   * @param value The parameter value.
   */
  void addTenantParameter(String key, String value) {
    StringBuilder sb = new StringBuilder(this.tenantParameters)
    if (this.tenantParameters?.isEmpty()) {
      sb.append("tenantParameters=").append(encode("${key}=${value}"))
    } else {
      sb.append(encode(",${key}=${value}"))
    }
    this.tenantParameters = sb.toString()
  }

  // method overloading, to accept boolean and string values
  void addTenantParameter(String key, boolean value) {
    addTenantParameter(key, String.valueOf(value))
  }

  InstallRequestParams doLoadReference(boolean is){
    addTenantParameter("loadReference", is)
    return this
  }

  InstallRequestParams doLoadSample(boolean is){
    addTenantParameter("loadSample", is)
    return this
  }

  /**
   * Removes a tenant parameter.
   *
   * @param key The key of the tenant parameter to be removed.
   */
  void removeTenantParameter(String key) {
    if (!this.tenantParameters?.isEmpty()) {
      String decodedParams = decode(this.tenantParameters.replace("tenantParameters=", ""))
      List<String> paramsList = decodedParams.split(",") as List<String>
      paramsList = paramsList.findAll { !it.startsWith(key) }
      if (paramsList.size() > 0) {
        this.tenantParameters = "tenantParameters=" + encode(paramsList.join(","))
      } else {
        this.tenantParameters = ""
      }
    }
  }

  /**
   * Converts the parameters to a query string.
   *
   * @return A string representing the query parameters.
   */
  String toQueryString() {
    def parameters = [:]
    def defaultValues = new InstallRequestParams()

    this.properties.each { property ->
      if (!defaultValues.properties.containsKey(property.key) || this[property.key] != defaultValues[property.key]) {
        if (property.key == 'tenantParameters' && this[property.key] != "") {
          parameters[property.key] = this[property.key].replace("tenantParameters=", "")
        } else {
          parameters[property.key] = encode("${property.value}")
        }
      }
    }

    def paramString = parameters.collect { key, value -> "$key=$value" }.join("&")

    if (!parameters.isEmpty()) {
      paramString = "?" + paramString
    }

    return paramString
  }

  /**
   * Encodes a string value to UTF-8.
   *
   * @param value The string to be encoded.
   * @return The encoded string.
   * @throws RuntimeException If the encoding operation fails.
   */
  private static String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8")
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Failed to encode string to UTF-8: " + value, e)
    }
  }

  /**
   * Decodes a UTF-8 encoded string.
   *
   * @param value The encoded string.
   * @return The decoded string.
   * @throws RuntimeException If the decoding operation fails.
   */
  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, "UTF-8")
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Failed to decode string from UTF-8: " + value, e)
    }
  }
}
