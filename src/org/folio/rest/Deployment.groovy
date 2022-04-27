package org.folio.rest

import org.folio.utilities.Logger

class Deployment implements Serializable {
    def steps
    private String okapiUrl
    private String repository
    private String branch

    private OkapiTenant tenant = new OkapiTenant()
    private OkapiUser admin_user = new OkapiUser()
    private OkapiUser super_admin = new OkapiUser(username: 'super_admin', password: 'admin')
    private OkapiUser testing_admin = new OkapiUser(username: 'testing_admin', password: 'admin')

    private Okapi okapi = new Okapi(steps, okapiUrl)
    private Users users = new Users(steps, okapiUrl)
    private Authorization auth = new Authorization(steps, okapiUrl)
    private Permissions permissions = new Permissions(steps, okapiUrl)
    private ServicePoints servicePoints = new ServicePoints(steps, okapiUrl)
    private GitHubUtility gitHubUtility = new GitHubUtility(steps)

    private Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    private ArrayList discoveryList = gitHubUtility.buildDiscoveryList(repository, branch)
    private ArrayList enableList = gitHubUtility.buildEnableList(repository, branch)

    Deployment(steps, okapiUrl, repository, branch, tenant, admin_user) {
        this.steps = steps
        this.okapiUrl = okapiUrl
        this.repository = repository
        this.branch = branch
        this.tenant = tenant
        this.admin_user = admin_user
    }

    void main() {
        try {
            if (tenant && admin_user) {
                okapi.pull()
                okapi.createTenant(tenant)
                okapi.registerServices(discoveryList)
                okapi.enableDisableUpgradeModulesForTenant(tenant, enableList, 900000)
                String authtokenModId = okapi.getModuleId(tenant, 'authtoken')
                ArrayList authtokenDisableDependenciesList = okapi.enableDisableUpgradeModulesForTenant(tenant, [[id: authtokenModId, action: "disable"]])
                admin_user.setUuid(users.createUser(tenant, admin_user))
                auth.createUserCredentials(tenant, admin_user)
                permissions.createUserPermissions(tenant, admin_user)
                String servicePointsUsersModId = okapi.getModuleId(tenant, 'service-points-users')
                if (servicePointsUsersModId) {
                    if (servicePoints.getServicePointsUsersRecords(tenant, admin_user).totalRecords == 0) {
                        servicePoints.createServicePointsUsersRecord(tenant, admin_user, servicePoints.getServicePointsIds(tenant))
                    }
                } else {
                    logger.warning('Module service-points-users does not installed')
                }
                okapi.enableDisableUpgradeModulesForTenant(tenant, authtokenDisableDependenciesList.reverse().collect { [id: it.id, action: "enable"] })
                def login = auth.login(tenant, admin_user)
                admin_user.setToken(login['token'])
                admin_user.setUuid(login['uuid'])
                admin_user.setPermissions(login['permissions'])
                admin_user.setPermissionsId(login['permissionsId'])
                tenant.setAdmin_user(admin_user)
                permissions.assignUserPermissions(tenant, admin_user, permissions.getAllPermissions(tenant))
                users.setPatronGroup(tenant, admin_user, users.getPatronGroupId(tenant, admin_user))
                okapi.secure(super_admin)
                okapi.setSuperuser(super_admin)
                okapi.secure(testing_admin)
            } else {
                throw new Exception('Tenant or admin user not set')
            }
        } catch (exception) {
            logger.error(exception.toString())
        }
    }
}
