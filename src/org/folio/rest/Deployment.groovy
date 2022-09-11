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

    private String kb_api_key

    private Email email = new Email()

    private OkapiTenant tenant = new OkapiTenant()

    private OkapiUser admin_user = new OkapiUser()

    private OkapiUser super_admin = new OkapiUser(username: 'super_admin', password: 'admin')

    private OkapiUser testing_admin = new OkapiUser(username: 'testing_admin', password: 'admin')

    private Okapi okapi = new Okapi(steps, okapi_url, super_admin)

    private GitHubUtility gitHubUtility = new GitHubUtility(steps)

    private TenantService tenantService = new TenantService(steps, okapi_url, super_admin)

    private boolean reindex = false

    private boolean recreate_index = false

    Deployment(Object steps, String okapi_url, String stripes_url, List install_json, Map install_map, OkapiTenant tenant, OkapiUser admin_user, Email email, String kb_api_key, reindex, recreate_index) {
        super(steps, okapi_url)
        this.stripes_url = stripes_url
        this.install_json = install_json
        this.install_map = install_map
        this.tenant = tenant
        this.admin_user = admin_user
        this.email = email
        this.kb_api_key = kb_api_key
        this.tenant.setAdmin_user(admin_user)
        this.reindex = reindex
        this.recreate_index = recreate_index
    }

    void main() {
        discovery_list = gitHubUtility.buildDiscoveryList(install_map)
        okapi.publishModuleDescriptors(install_json)
        okapi.registerServices(discovery_list)

        okapi.secure(super_admin)
        okapi.secure(testing_admin)

        tenantService.createTenant(tenant, admin_user, install_json, email, stripes_url, kb_api_key, reindex, recreate_index)
    }

    void cleanup(){
        okapi.cleanupServicesRegistration()
    }

    void update() {
        if (tenant) {
            discovery_list = gitHubUtility.buildDiscoveryList(install_map)
            okapi.publishModuleDescriptors(install_json)
            okapi.registerServices(discovery_list)
            okapi.enableDisableUpgradeModulesForTenant(tenant, okapi.buildInstallList(["okapi"], "enable"))
            okapi.enableDisableUpgradeModulesForTenant(tenant, install_json, 900000)
            if (reindex) {
                def job_id = okapi.reindexElasticsearch(tenant, admin_user, recreate_index)
                okapi.checkReindex(tenant, job_id)
            }
        } else {
            throw new AbortException('Tenant not set')
        }
    }
}
