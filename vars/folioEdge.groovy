import groovy.text.StreamingTemplateEngine
import org.folio.models.OkapiTenant
import org.folio.models.RancherNamespace
import org.folio.utilities.Tools

//TODO: ***Additional improvement in logic & refactoring required

/**
 * Renders the ephemeral properties for a tenant.
 *
 * @param tenant The tenant for which the ephemeral properties are to be rendered.
 * @param Url identifies Okapi domain URL
 *
 */

void renderEphemeralProperties(OkapiTenant tenant, RancherNamespace ns) {
    Tools tools = new Tools(this)
    Map edgeUsersConfig = tools.steps.readYaml file: tools.copyResourceFileToWorkspace("edge/config.yaml")
    String defaultTenantId = tenant.tenantId
    String config_template = tools.steps.readFile file: tools.copyResourceFileToWorkspace("edge/ephemeral-properties.tpl")
    tenant.modules.edgeModules.each { name, version ->
        String edgeTenantsId = defaultTenantId
        String institutional = ""
        String admin_users = ""
        Map edgeUserConfig = edgeUsersConfig[(name)]
        if (edgeUserConfig['tenants']) {
            edgeUserConfig['tenants'].each {
                Map obj = [tenant  : it.tenant == "default" ? defaultTenantId : it.tenant,
                           username: it.username,
                           password: it.tenant == "default" ? defaultTenantId : it.password]
                edgeTenantsId += it.tenant == "default" ? "" : "," + it.tenant
                institutional += obj.tenant + "=" + obj.username + "," + obj.password + "\n"
            }
        }
        ns.tenants.each { tenant_name, tenant_cm ->
            if (tenant_cm.tenantId != "supertenant" || tenant_cm.tenantId != 'diku') {
                admin_users += "${tenant_cm.tenantId + "=" + tenant_cm.adminUser.username + "," + tenant_cm.adminUser.password + "\n"}"
                edgeTenantsId += "," + tenant_cm.tenantId
            }
        }
        LinkedHashMap config_data = [edge_tenants: edgeTenantsId, edge_mappings: defaultTenantId, edge_users: admin_users, institutional_users: institutional]
        tools.steps.writeFile file: "${name}-ephemeral-properties", text: (new StreamingTemplateEngine().createTemplate(config_template).make(config_data)).toString()
    }
}
