package org.folio.rest

import hudson.AbortException
import org.folio.rest.model.Email
import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

class Deployment extends GeneralParameters {

    private String stripesUrl

    private String branch

    private String repository

    private List enableList

    private List discoveryList

    private String kb_api_key

    private Email email = new Email()

    private OkapiTenant tenant = new OkapiTenant()

    private OkapiUser admin_user = new OkapiUser()

    private OkapiUser super_admin = new OkapiUser(username: 'super_admin', password: 'admin')

    private OkapiUser testing_admin = new OkapiUser(username: 'testing_admin', password: 'admin')

    private Okapi okapi = new Okapi(steps, okapiUrl, super_admin)

    private Users users = new Users(steps, okapiUrl)

    private Authorization auth = new Authorization(steps, okapiUrl)

    private Permissions permissions = new Permissions(steps, okapiUrl)

    private ServicePoints servicePoints = new ServicePoints(steps, okapiUrl)

    private GitHubUtility gitHubUtility = new GitHubUtility(steps)

    private TenantConfiguration tenantConfiguration = new TenantConfiguration(steps, okapiUrl)

    private Edge edge = new Edge(steps, okapiUrl)

    Deployment(Object steps, String okapiUrl, String stripesUrl, String repository, String branch, OkapiTenant tenant, OkapiUser admin_user, Email email, String kb_api_key) {
        super(steps, okapiUrl)
        this.stripesUrl = stripesUrl
        this.repository = repository
        this.branch = branch
        this.tenant = tenant
        this.admin_user = admin_user
        this.email = email
        this.kb_api_key = kb_api_key
        this.tenant.setAdmin_user(admin_user)
    }

    void main() {
        if (tenant && admin_user) {
            enableList = gitHubUtility.buildEnableList(repository, branch)
            discoveryList = gitHubUtility.buildDiscoveryList(repository, branch)
            okapi.publishModuleDescriptors(enableList)
            okapi.createTenant(tenant)
            okapi.enableDisableUpgradeModulesForTenant(tenant, okapi.buildInstallList(["okapi"], "enable"))
            okapi.registerServices(discoveryList)
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
            auth.login(tenant, admin_user)
            permissions.assignUserPermissions(tenant, admin_user, permissions.getAllPermissions(tenant))
            users.setPatronGroup(tenant, admin_user, users.getPatronGroupId(tenant, admin_user))
            okapi.secure(super_admin)
            okapi.secure(testing_admin)
            edge.createEdgeUsers(tenant, enableList)
            tenantConfiguration.modInventoryMods(tenant)
            tenantConfiguration.ebscoRmapiConfig(tenant, kb_api_key)
            tenantConfiguration.worldcat(tenant)
            tenantConfiguration.configurations(tenant, email, stripesUrl)
        } else {
            throw new AbortException('Tenant or admin user not set')
        }
    }
    void update() {
        if (tenant) {
            enableList = gitHubUtility.buildEnableList(repository, branch)
            discoveryList = gitHubUtility.buildDiscoveryList(repository, branch)
            // insert new method for cleanup
            okapi.publishModuleDescriptors(enableList)
            okapi.enableDisableUpgradeModulesForTenant(tenant, okapi.buildInstallList(["okapi"], "enable"))
            okapi.registerServices(discoveryList)
            okapi.enableDisableUpgradeModulesForTenant(tenant, enableList, 900000)
        }  else {
            throw new AbortException('Tenant not set')
        }
    }
}
