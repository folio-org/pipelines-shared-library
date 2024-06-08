package org.folio.rest_v2

import org.folio.models.OkapiTenant
import org.folio.models.OkapiUser

/**
 * Edge is a class that extends the Common class.
 * It is responsible for managing Edge users and their associated configurations.
 */
class Edge extends Authorization {

    private static final String EDGE_USERS_CONFIG_PATH = 'edge/config.yaml'
    private Users users
    private Permissions permissions


    /**
     * Initializes a new instance of the Edge class.
     *
     * @param context The current context.
     * @param okapiDomain The domain for Okapi.
     * @param superTenant The super tenant for the system.
     * @param debug Debug flag indicating whether debugging is enabled.
     */
    Edge(Object context, String okapiDomain, boolean debug = false) {
        super(context, okapiDomain, debug)
        this.users = new Users(context, okapiDomain, debug)
        this.permissions = new Permissions(context, okapiDomain, debug)
    }

    /**
     * Creates Edge users for a tenant.
     *
     * @param tenant The tenant for which Edge users are to be created.
     */
    void createEdgeUsers(OkapiTenant tenant) {
        Map edgeUsersConfig = steps.readYaml file: tools.copyResourceFileToWorkspace(EDGE_USERS_CONFIG_PATH)
        List edgePermissionsList = []

        tenant.modules.edgeModules.each { name, version ->
            def edgeUserConfig = edgeUsersConfig[(name)]
            def edgePermissions = edgeUserConfig['permissions']
            def tenants = edgeUserConfig['tenants']

            if (edgePermissions) {
                edgePermissionsList.addAll(edgePermissions)
            }
            if (tenants) {
                tenants.each {
                    if (it['tenant'] == 'default' && it['create']) {
                        OkapiUser edgeUser = new OkapiUser(it['username'], it['password'] == "default" ? tenant.tenantId : it['password'])
                            .withFirstName(it['firstName'])
                            .withLastName(it['lastName'])
                            .withPermissions(edgePermissions)
                        createEdgeUser(tenant, edgeUser)
                    }
                }
            }
        }

        OkapiUser defaultEdgeUser = new OkapiUser(tenant.tenantId, tenant.tenantId)
            .withFirstName('EDGE')
            .withLastName('SYSTEM')
            .withPermissions(edgePermissionsList)

        createEdgeUser(tenant, defaultEdgeUser)
    }

    private void createEdgeUser(OkapiTenant tenant, OkapiUser edgeUser) {
        users.createUser(tenant, edgeUser)
        permissions.createUserPermissions(tenant, edgeUser)
        setUserCredentials(tenant, edgeUser)
    }
}
