package org.folio.rest_v2

import org.folio.models.OkapiTenantConsortia

/**
 * Consortia is a class that extends the Authorization class.
 * It is responsible for managing consortia in the system. It can create a consortia,
 * add a central consortia tenant and add a tenant to the consortia.
 */
class Consortia extends Authorization {

    /**
     * Initializes a new instance of the Consortia class.
     *
     * @param context The current context.
     * @param okapiDomain The domain for Okapi.
     * @param debug Debug flag indicating whether debugging is enabled.
     */
    Consortia(Object context, String okapiDomain, boolean debug = false) {
        super(context, okapiDomain, debug)
    }

    /**
     * Creates a new consortia.
     *
     * @param centralConsortiaTenant The central tenant of the consortia.
     * @return The ID of the newly created consortia.
     */
    String createConsortia(OkapiTenantConsortia centralConsortiaTenant) {
        if (!centralConsortiaTenant.isCentralConsortiaTenant) {
            logger.error("${centralConsortiaTenant.tenantId} is not a central consortia tenant")
        }

        centralConsortiaTenant.consortiaUuid = UUID.randomUUID().toString()

        String url = generateUrl("/consortia")
        Map<String, String> headers = getAuthorizedHeaders(centralConsortiaTenant)
        Map body = [
            "id"  : centralConsortiaTenant.consortiaUuid,
            "name": centralConsortiaTenant.consortiaName
        ]

        Map response = restClient.post(url, body, headers).body
        logger.info("Consortia (${centralConsortiaTenant.consortiaName}) created. UUID: ${centralConsortiaTenant.consortiaUuid}")
        return response.id
    }

    /**
     * Adds a central consortia tenant to a consortia.
     *
     * @param centralConsortiaTenant The central tenant of the consortia.
     */
    void addCentralConsortiaTenant(OkapiTenantConsortia centralConsortiaTenant) {
        if (!centralConsortiaTenant.isCentralConsortiaTenant) {
            logger.error("${centralConsortiaTenant.tenantId} is not a central consortia tenant")
        }

        String url = generateUrl("/consortia/${centralConsortiaTenant.consortiaUuid}/tenants")
        Map<String, String> headers = getAuthorizedHeaders(centralConsortiaTenant)
        Map body = [
            "id"       : centralConsortiaTenant.tenantId,
            "name"     : centralConsortiaTenant.tenantName,
            "code"     : centralConsortiaTenant.tenantCode,
            "isCentral": true
        ]
        restClient.post(url, body, headers).body
        logger.info("${centralConsortiaTenant.tenantId} successfully added to ${centralConsortiaTenant.consortiaName} consortia")
    }

    /**
     * Adds a tenant to a consortia.
     *
     * @param centralConsortiaTenant The central tenant of the consortia.
     * @param institutionalTenant The tenant to be added to the consortia.
     */
    void addConsortiaTenant(OkapiTenantConsortia centralConsortiaTenant, OkapiTenantConsortia institutionalTenant) {
        if (institutionalTenant.isCentralConsortiaTenant) {
            logger.error("${institutionalTenant.tenantId} is a central consortia tenant")
        }
        centralConsortiaTenant.adminUser.checkUuid()

        String url = generateUrl("/consortia/${centralConsortiaTenant.consortiaUuid}/tenants?adminUserId=${centralConsortiaTenant.adminUser.uuid}")
        Map<String, String> headers = getAuthorizedHeaders(centralConsortiaTenant)
        Map body = [
            "id"       : institutionalTenant.tenantId,
            "name"     : institutionalTenant.tenantName,
            "code"     : institutionalTenant.tenantCode,
            "isCentral": false
        ]
        restClient.post(url, body, headers).body
        logger.info("${institutionalTenant.tenantId} successfully added to ${centralConsortiaTenant.consortiaName} consortia")
    }

    /**
     * Sets up a consortia with the given tenants.
     *
     * @param consortiaTenants A map of consortia tenants.
     */
    void setUpConsortia(List<OkapiTenantConsortia> consortiaTenants) {
        OkapiTenantConsortia centralConsortiaTenant = consortiaTenants.find { it.isCentralConsortiaTenant }
        createConsortia(centralConsortiaTenant)
        addCentralConsortiaTenant(centralConsortiaTenant)
        // Required sleep for 2 minutes because consortia async installation
        sleep(1 * 60 * 1000)
        consortiaTenants.findAll { (!it.isCentralConsortiaTenant) }.each { institutionalTenant ->
            addConsortiaTenant(centralConsortiaTenant, institutionalTenant)
            // Required sleep for 2 minutes because consortia async installation
            sleep(1 * 60 * 1000)
        }
    }
}
