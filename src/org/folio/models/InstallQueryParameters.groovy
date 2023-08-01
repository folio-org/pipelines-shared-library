package org.folio.models

/**
 * This class defines the parameters required for installation query.
 */
class InstallQueryParameters implements Cloneable {
    boolean async
    boolean ignoreErrors
    boolean reinstall
    boolean simulate
    String tenantParameters

    InstallQueryParameters withAsync(boolean async) {
        this.async = async
        return this
    }

    InstallQueryParameters withIgnoreErrors(boolean ignoreErrors) {
        this.ignoreErrors = ignoreErrors
        return this
    }

    InstallQueryParameters withReinstall(boolean reinstall) {
        this.reinstall = reinstall
        return this
    }

    InstallQueryParameters withSimulate(boolean simulate) {
        this.simulate = simulate
        return this
    }

    InstallQueryParameters withTenantParameters(String tenantParameters) {
        this.tenantParameters = "tenantParameters=" + encode(tenantParameters)
        return this
    }

    InstallQueryParameters clone() {
        try {
            super.clone() as InstallQueryParameters
        } catch(CloneNotSupportedException e) {
            throw new AssertionError('This should not happen: ' + e)
        }
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

    void addTenantParameter(String key, boolean value) {
        addTenantParameter(key, String.valueOf(value))
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
        def defaultValues = new InstallQueryParameters()

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
