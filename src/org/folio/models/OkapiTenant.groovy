package org.folio.models

/**
 * OkapiTenant class representing a tenant configuration for Okapi.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class OkapiTenant {
    String tenantId
    String tenantName
    String tenantDescription
    OkapiUser adminUser
    String okapiVersion
    Modules modules
    Index index
    InstallQueryParameters installQueryParameters
    OkapiConfig config
    TenantUi tenantUi

    OkapiTenant(String tenantId) {
        this.tenantId = tenantId
        this.modules = new Modules()
        this.installQueryParameters = new InstallQueryParameters()
        this.config = new OkapiConfig()
    }

    // Chainable setters
    OkapiTenant withTenantName(String tenantName) {
        this.tenantName = tenantName
        return this
    }

    OkapiTenant withTenantDescription(String tenantDescription) {
        this.tenantDescription = tenantDescription
        return this
    }

    OkapiTenant withAdminUser(OkapiUser adminUser) {
        this.adminUser = adminUser
        return this
    }

    OkapiTenant withOkapiVersion(String okapiVersion) {
        this.okapiVersion = okapiVersion
        return this
    }

    /**
     * Chainable setter for install JSON.
     * It removes 'mod-consortia' and 'folio_consortia-settings' modules.
     * @param installJson The install JSON object.
     * @return The OkapiTenant object.
     */
    OkapiTenant withInstallJson(Object installJson) {
        this.modules.setInstallJson(installJson)
        this.modules.removeModules(['mod-consortia', 'folio_consortia-settings'])
        return this
    }

    OkapiTenant withIndex(boolean run, boolean recreate, boolean waitComplete = true) {
        this.index = new Index(run, recreate, waitComplete)
        return this
    }

    OkapiTenant withInstallQueryParameters(InstallQueryParameters installQueryParameters) {
        this.installQueryParameters = installQueryParameters.clone()
        return this
    }

    /**
     * Chainable setter for Okapi configuration.
     * It performs a deep copy of the configuration object.
     * @param config The OkapiConfig object.
     * @return The OkapiTenant object.
     */
    OkapiTenant withConfiguration(OkapiConfig config) {
        this.config = config
        return this
    }

    OkapiTenant withTenantUi(TenantUi tenantUi) {
        this.tenantUi = tenantUi.withTenantId(this.tenantId)
        return this
    }
}
