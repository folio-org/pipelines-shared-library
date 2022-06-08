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
        enableList = gitHubUtility.buildEnableList(repository, branch)
        discoveryList = gitHubUtility.buildDiscoveryList(repository, branch)
        okapi.publishModuleDescriptors(enableList)
        okapi.registerServices(discoveryList)
        okapi.secure(super_admin)
        okapi.secure(testing_admin)
        def tenantService = new TenantService(steps, okapiUrl, super_admin)
        tenantService.createTenant(tenant, admin_user, enableList, email, stripesUrl, kb_api_key)
    }
    void update() {
        if (tenant) {
            enableList = gitHubUtility.buildEnableList(repository, branch)
            discoveryList = gitHubUtility.buildDiscoveryList(repository, branch)
            okapi.cleanupServicesRegistration()
            okapi.publishModuleDescriptors(enableList)
            okapi.enableDisableUpgradeModulesForTenant(tenant, okapi.buildInstallList(["okapi"], "enable"))
            okapi.registerServices(discoveryList)
            okapi.enableDisableUpgradeModulesForTenant(tenant, enableList, 900000)
        }  else {
            throw new AbortException('Tenant not set')
        }
    }
}
