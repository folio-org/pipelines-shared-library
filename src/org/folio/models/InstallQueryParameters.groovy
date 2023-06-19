package org.folio.models

class InstallQueryParameters {
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

    void addTenantParameter(String key, boolean value) {
        if (this.tenantParameters?.isEmpty()) {
            this.tenantParameters += "tenantParameters=" + encode("${key}=${value}")
        } else {
            this.tenantParameters += encode(",${key}=${value}")
        }
    }

    void removeTenantParameter(String key) {
        if (!this.tenantParameters?.isEmpty()) {
            String decodedParams = decode(this.tenantParameters.replace("tenantParameters=", ""))
            List<String> paramsList = decodedParams.split(",") as List<String>
            paramsList.removeIf { it.startsWith(key) }
            if (paramsList.size() > 0) {
                this.tenantParameters = "tenantParameters=" + encode(paramsList.join(","))
            } else {
                this.tenantParameters = ""
            }
        }
    }

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

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8")
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e)
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8")
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e)
        }
    }
}
