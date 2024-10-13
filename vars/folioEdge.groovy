import groovy.json.JsonSlurperClassic
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
  Common common = new Common(this, "https://${namespace.generateDomain('kong')}")
  Map edgeConfig = tools.steps.readYaml file: tools.copyResourceFileToCurrentDirectory("edge/config_eureka.yaml")
  String config_template = tools.steps.readFile file: tools.copyResourceFileToCurrentDirectory("edge/ephemeral-properties.tpl")
  List mappings = []
  String users = ''

  def json = tools.steps.sh(script: "curl --silent https://${namespace.generateDomain('kong')}/tenants", returnStdout: true)

  common.logger.info("Response: ${json}")

  def dataToProcess = tools.jsonParse(json as String)

  common.logger.info("List of existing tenants: ${dataToProcess['tenants']['name']}")

  if ('fs09000000' in dataToProcess['tenants']['name']) { // to the mappings part
    mappings.add('fs09000000')
  } else {
    mappings.add('diku')
  }

  def tenants = dataToProcess['tenants']['name'] as List

  dataToProcess['tenants'].each { candidate -> // real existing tenant's metadata include
    common.logger.info("Binding tenant: " + candidate['name'])
    def tenant = folioDefault.tenants()[candidate['name']]
    users += tenant.getTenantId() + '=' + tenant.getAdminUser().getUsername() + ',' + tenant.getAdminUser().getPasswordPlainText() + '\n'
    common.logger.info("Tenant: " + candidate['name'] + " bind complete.")
  }

  namespace.getModules().getEdgeModules().each { name, version ->
    String institutionalUsers = ''
    if (edgeConfig[name]['tenants']) {
      edgeConfig[name]['tenants'].each { institutional ->
        tenants.add(institutional.tenant)
        institutionalUsers += "${(institutional.tenant == 'default' ? mappings.getAt(0) : institutional.tenant)}=${institutional.username},${institutional.password}\n"
      }
    }
    LinkedHashMap config_data = [edge_tenants: "${tenants.join(",")}", edge_mappings: "${mappings.getAt(0)}", edge_users: users + institutionalUsers, institutional_users: 'test=test,test']
    tools.steps.writeFile file: "${name}-ephemeral-properties", text: (new StreamingTemplateEngine().createTemplate(config_template).make(config_data)).toString()
    common.logger.info("ephemeralProperties file for module ${name} created.")
  }
}
