package org.folio.models

/**
 * OkapiTenantConsortia class is a subclass of OkapiTenant
 * representing a tenant configuration specifically for consortia.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class OkapiTenantConsortia extends OkapiTenant {
    boolean isCentralConsortiaTenant
    String consortiaName
    String consortiaUuid
    String tenantCode

    OkapiTenantConsortia(String tenantId, boolean isCentralConsortiaTenant = false) {
        super(tenantId)
        this.isCentralConsortiaTenant = isCentralConsortiaTenant
    }

    OkapiTenantConsortia withTenantCode(String tenantCode) {
        this.tenantCode = tenantCode
        return this
    }

    OkapiTenantConsortia withConsortiaName(String consortiaName) {
        this.consortiaName = consortiaName
        return this
    }

    /**
     * Chainable setter for install JSON.
     * It removes 'folio_consortia-settings' module for non-central consortia tenants.
     * @param installJson The install JSON object.
     * @return The OkapiTenantConsortia object.
     */
    OkapiTenantConsortia withInstallJson(Object installJson) {
        this.getModules().setInstallJson(installJson)
        if (!this.isCentralConsortiaTenant) {
            this.getModules().removeModule('folio_consortia-settings')
        }
        return this
    }

    /**
     * Chainable setter for install query parameters.
     * It removes "loadSample" tenant parameter for non-central consortia tenants.
     * @param installQueryParameters The InstallQueryParameters object.
     * @return The OkapiTenantConsortia object.
     */
    OkapiTenantConsortia withInstallQueryParameters(InstallQueryParameters installQueryParameters) {
        super.withInstallQueryParameters(installQueryParameters)
        if (!this.isCentralConsortiaTenant) {
            this.getInstallQueryParameters().removeTenantParameter("loadSample")
        }
        return this
    }

    /**
     * Private helper method to check and remove modules.
     * It removes 'folio_consortia-settings' module for non-central consortia tenants.
     */
    private void checkAndRemoveModules() {
        if (!this.isCentralConsortiaTenant) {
            this.getModules().removeModule('folio_consortia-settings')
        }
    }
}
