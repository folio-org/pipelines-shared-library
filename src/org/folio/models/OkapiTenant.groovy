package org.folio.models

class OkapiTenant {
    String tenantId
    String tenantName
    String tenantDescription
    OkapiUser adminUser
    String okapiVersion
    Modules modules
    Index index
    InstallQueryParameters installQueryParameters
    Map domains
    TenantUi tenantUi
    SmtpConfig smtpConfig
    String kbApiKey

    private static final List VALID_DOMAIN_KEYS = ['ui', 'okapi', 'edge']

    OkapiTenant(String tenantId) {
        this.tenantId = tenantId
        this.index = new Index()
        this.installQueryParameters = new InstallQueryParameters()
        this.domains = [:]
        this.modules = new Modules()
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

    OkapiTenant withInstallJson(Object installJson) {
        this.modules = new Modules().withInstallJson(installJson)
        this.modules.removeModule('mod-consortia')
        this.modules.removeModule('folio_consortia-settings')
        return this
    }

    OkapiTenant withIndex(boolean index, boolean recreate, boolean waitComplete = true) {
        this.index = new Index(index, recreate, waitComplete)
        return this
    }

    OkapiTenant withInstallQueryParameters(InstallQueryParameters installQueryParameters) {
        this.installQueryParameters = installQueryParameters
        return this
    }

    OkapiTenant withDomains(Map domains) {
        setDomains(domains)
        return this
    }

    OkapiTenant withOkapiSmtp(SmtpConfig smtpConfig) {
        this.smtpConfig = smtpConfig
        return this
    }

    OkapiTenant withKbApiKey(String kbApiKey) {
        this.kbApiKey = kbApiKey
        return this
    }

    OkapiTenant withTenantUi(TenantUi tenantUi) {
        this.tenantUi = tenantUi.withTenantId(this.tenantId)
        return this
    }

    static void validateDomainKey(String key) {
        if (!VALID_DOMAIN_KEYS.contains(key)) {
            throw new IllegalArgumentException("Invalid key in domains map: ${key}")
        }
    }

    void setDomains(Map domains) {
        domains.each { key, value ->
            validateDomainKey(key)
        }
        this.domains = domains
    }

    void addDomain(String key, String value) {
        validateDomainKey(key)
        this.domains[key] = value
    }

    void removeDomain(String key) {
        if (this.domains.containsKey(key)) {
            this.domains.remove(key)
        }
    }
}
