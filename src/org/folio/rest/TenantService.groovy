package org.folio.rest

import hudson.AbortException
import org.folio.rest.model.Email
import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

class TenantService extends GeneralParameters {

    private OkapiUser okapiAdmin

    private Okapi okapi = new Okapi(steps, okapi_url, okapiAdmin)

    private Authorization auth = new Authorization(steps, okapi_url)

    private Users users = new Users(steps, okapi_url)

    private Permissions permissions = new Permissions(steps, okapi_url)

    private ServicePoints servicePoints = new ServicePoints(steps, okapi_url)

    private Edge edge = new Edge(steps, okapi_url)

    private TenantConfiguration tenantConfiguration = new TenantConfiguration(steps, okapi_url)

    TenantService(Object steps, String okapi_url, OkapiUser okapiAdmin) {
        super(steps, okapi_url)
        this.okapiAdmin = okapiAdmin
    }

    void createTenant(OkapiTenant tenant, OkapiUser admin_user, List enableList, Email email, String stripes_url, String kb_api_key, reindex_elastic_search, recreate_index_elastic_search) {
        if (tenant && admin_user) {
            okapi.createTenant(tenant)
            okapi.enableDisableUpgradeModulesForTenant(tenant, okapi.buildInstallList(["okapi"], "enable"))
            okapi.enableDisableUpgradeModulesForTenant(tenant, enableList, 900000)
            String authtokenModId = okapi.getModuleId(tenant, 'authtoken')
            List authtokenDisableDependenciesList = okapi.enableDisableUpgradeModulesForTenant(tenant, okapi.buildInstallList([authtokenModId], "disable"))
            users.createUser(tenant, admin_user)
            auth.createUserCredentials(tenant, admin_user)
            permissions.createUserPermissions(tenant, admin_user)
            String servicePointsUsersModId = okapi.getModuleId(tenant, 'service-points-users')
            if (servicePointsUsersModId) {
                if (servicePoints.getServicePointsUsersRecords(tenant, admin_user).totalRecords == 0) {
                    servicePoints.createServicePointsUsersRecord(tenant, admin_user, servicePoints.getServicePointsIds(tenant))
                }
            } else {
                logger.warning("Module service-points-users does not installed")
            }
            okapi.enableDisableUpgradeModulesForTenant(tenant, authtokenDisableDependenciesList.reverse().collect { [id: it.id, action: "enable"] })
            auth.login(tenant, tenant.admin_user)
            permissions.assignUserPermissions(tenant, admin_user, permissions.getAllPermissions(tenant))
            users.setPatronGroup(tenant, tenant.admin_user, users.getPatronGroupId(tenant, admin_user))
            if (reindex_elastic_search) {
                def jobid = okapi.reindexElasticsearch(tenant, admin_user, recreate_index_elastic_search)
                okapi.checkReindex(tenant, jobid)
            }
            tenantConfiguration.modInventoryMods(tenant)
            tenantConfiguration.ebscoRmapiConfig(tenant, kb_api_key)
            tenantConfiguration.worldcat(tenant)
            tenantConfiguration.configurations(tenant, email, stripes_url)
        } else {
            throw new AbortException('Tenant or admin user not set')
        }
    }
}
