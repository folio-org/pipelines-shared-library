package org.folio.models

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

    OkapiTenantConsortia withInstallJson(Object installJson) {
        this.getModules().withInstallJson(installJson)
        checkAndRemoveModules()
        return this
    }

    private void checkAndRemoveModules() {
        if (!this.isCentralConsortiaTenant) {
            this.getModules().removeModule('folio_consortia-settings')
        }
    }
}
