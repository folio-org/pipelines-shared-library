import groovy.text.StreamingTemplateEngine
import org.folio.Constants
import org.folio.models.RancherNamespace
import org.folio.rest_v2.Common
import org.folio.utilities.RestClient
import org.folio.utilities.Tools

/**
 * Renders the ephemeral properties for a tenant.
 *
 * @param tenant The tenant for which the ephemeral properties are to be rendered.
 * @param namespace represents RancherNamespace object with ALL properties.
 *
 */

void renderEphemeralProperties(RancherNamespace namespace) {
  Tools tools = new Tools(this)
  Common common = new Common(this, "https://${namespace.getDomains()['okapi']}")
  Map edgeUsersConfig = tools.steps.readYaml file: tools.copyResourceFileToCurrentDirectory("edge/config.yaml")
  String defaultTenantId = namespace.getDefaultTenantId()
  String config_template = tools.steps.readFile file: tools.copyResourceFileToCurrentDirectory("edge/ephemeral-properties.tpl")
  namespace.getModules().getEdgeModules().each { name, version ->
    String edgeTenantsId = defaultTenantId
    String institutional = ""
    String admin_users = ""
    Map edgeUserConfig = edgeUsersConfig[(name)]
    try {
      if (edgeUserConfig['tenants']) {
        edgeUserConfig['tenants'].each {
          Map obj = [tenant  : it.tenant == "default" ? defaultTenantId : it.tenant,
                     username: it.username,
                     password: it.tenant == "default" ? defaultTenantId : it.password]
          edgeTenantsId += it.tenant == "default" ? "" : "," + it.tenant
          institutional += obj.tenant + "=" + obj.username + "," + obj.password + "\n"
        }
      }
      namespace.tenants.each { tenantId, tenant ->
        if (tenant.getAdminUser()) {
          switch (tenant.tenantId) {
            case "supertenant":
              common.logger.warning("The ${tenant.tenantId} should not be presented in config, aborted!")
              break
            case "diku":
              admin_users += "${tenant.tenantId + "=" + tenant.adminUser.username + "," + tenant.adminUser.password + "\n"}"
              break
            case ['cs00000int', 'cs00000int_0001', 'cs00000int_0002', 'cs00000int_0003', 'cs00000int_0004', 'cs00000int_0005']:
              admin_users += "${tenant.tenantId + "=" + Constants.ECS_EDGE_GENERAL_USERNAME + "," + Constants.ECS_EDGE_GENERAL_PASSWORD + "\n"}"
              break
            default:
              admin_users += "${tenant.tenantId + "=" + tenant.adminUser.username + "," + tenant.adminUser.password + "\n"}"
              edgeTenantsId += "," + tenant.tenantId
              break
          }
        }
      }
      LinkedHashMap config_data = [edge_tenants: edgeTenantsId, edge_mappings: defaultTenantId, edge_users: admin_users, institutional_users: institutional]
      tools.steps.writeFile file: "${name}-ephemeral-properties", text: (new StreamingTemplateEngine().createTemplate(config_template).make(config_data)).toString()
    } catch (Exception e) {
      common.logger.warning("Faulty module name: ${name}, error: ${e.getMessage()}")
    }
  }
}

void renderEphemeralPropertiesEureka(RancherNamespace namespace) {
  Tools tools = new Tools(this)
  Common common = new Common(this, "https://${namespace.getDomains()['okapi']}")
  Map edgeUsersConfig = tools.steps.readYaml file: tools.copyResourceFileToCurrentDirectory("edge/config_eureka.yaml")
  String config_template = tools.steps.readFile file: tools.copyResourceFileToCurrentDirectory("edge/ephemeral-properties.tpl")
  List tenants = []
  List mappings = []
  RestClient client = new RestClient(this)
  def json = client.get("https://${namespace.generateDomain('kong')}/tenants").body
  if ('fs09000000' in json['tenants']) {
    mappings.add('fs09000000')
  } else {
    mappings.add('diku')
  }
  json['tenants'].each { tenant ->
    tenants.add(tenant['name'])
  }
}
