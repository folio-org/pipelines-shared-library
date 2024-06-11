package org.folio.rest


import hudson.AbortException
import org.folio.rest.model.Email
import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

class Deployment extends GeneralParameters {

  private String stripes_url

  private List install_json

  private Map install_map

  private List discovery_list

  private Email email = new Email()

  private OkapiTenant tenant = new OkapiTenant()

  private OkapiUser admin_user = new OkapiUser()

  private OkapiUser super_admin = new OkapiUser()

  private OkapiUser testing_admin = new OkapiUser(username: 'testing_admin', password: 'admin')

  private OkapiUser mod_search_user = new OkapiUser(username: "mod-search", password: "Mod-search-1-0-0")

  private Okapi okapi = new Okapi(steps, okapi_url, super_admin)

  private Authorization auth = new Authorization(steps, okapi_url)

  private Permissions permissions = new Permissions(steps, okapi_url)

  private GitHubUtility gitHubUtility = new GitHubUtility(steps)

  private TenantService tenantService = new TenantService(steps, okapi_url, super_admin)

  private boolean restore_from_backup

  Deployment(Object steps, String okapi_url, String stripes_url, List install_json, Map install_map, OkapiTenant tenant, OkapiUser admin_user, OkapiUser super_admin, Email email, boolean restore_from_backup = false) {
    super(steps, okapi_url)
    this.stripes_url = stripes_url
    this.install_json = install_json
    this.install_map = install_map
    this.tenant = tenant
    this.admin_user = admin_user
    this.super_admin = super_admin
    this.email = email
    this.tenant.setAdminUser(admin_user)
    this.restore_from_backup = restore_from_backup
  }

  void main() {
    discovery_list = gitHubUtility.buildDiscoveryList(install_map)
    okapi.publishModulesDescriptors(okapi.composeModulesDescriptors(install_json))
    okapi.registerServices(discovery_list)

    okapi.secure(super_admin)
    okapi.secure(testing_admin)

    tenantService.createTenant(tenant, admin_user, install_json, email, stripes_url)
  }

  void cleanup() {
    //This function breaks pipeline but is required in case you are working with non-bugfest rds snapshot.
//        okapi.unsecure()
    okapi.cleanupServicesRegistration()
  }

  void unsecure() {
    okapi.unsecure()
  }

  void update() {
    if (tenant) {
      discovery_list = gitHubUtility.buildDiscoveryList(install_map)
      okapi.publishModulesDescriptors(okapi.composeModulesDescriptors(install_json))
      okapi.registerServices(discovery_list)
      okapi.enableDisableUpgradeModulesForTenant(tenant, okapi.buildInstallList(["okapi"], "enable"))
      okapi.enableDisableUpgradeModulesForTenant(tenant, install_json, 900000)
      if (restore_from_backup) {
        Users user = new Users(steps, okapi_url)
        user.resetUserPassword(tenant, mod_search_user)
      }
      if (tenant.index.reindex) {
        def job_id = okapi.reindexElasticsearch(tenant, admin_user)
        okapi.checkReindex(tenant, job_id)
      }
    } else {
      throw new AbortException('Tenant not set')
    }
  }

  void installSingleBackendModule(List descriptor = []) {
    if (tenant) {
      discovery_list = gitHubUtility.buildDiscoveryList(install_map)
      okapi.publishModulesDescriptors(descriptor)
      okapi.registerServices(discovery_list)
      okapi.enableDisableUpgradeModulesForTenant(tenant, install_json, 900000)
      auth.login(tenant, tenant.getAdminUser())
      permissions.assignUserPermissions(tenant, admin_user, permissions.getAllPermissions(tenant))
      if (tenant.index.reindex) {
        def job_id = okapi.reindexElasticsearch(tenant, admin_user)
        okapi.checkReindex(tenant, job_id)
      }
    } else {
      throw new AbortException('Tenant not set')
    }
  }

  void createTenant() {
    tenantService.createTenant(tenant, admin_user, install_json, email, stripes_url)
  }

  void updatePermissionsAll() {
    auth.login(tenant, tenant.getAdminUser())
    permissions.assignUserPermissions(tenant, admin_user, permissions.getAllPermissions(tenant))
  }
}
