package org.folio.rest

import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

class Edge extends GeneralParameters {

  private Users users = new Users(steps, okapi_url)

  private Authorization auth = new Authorization(steps, okapi_url)

  private Permissions permissions = new Permissions(steps, okapi_url)

  Edge(Object steps, String okapi_url) {
    super(steps, okapi_url)
  }

  void renderEphemeralProperties(Map install_edge_map, OkapiTenant default_tenant, OkapiUser default_user) {
    def file_path = tools.copyResourceFileToWorkspace('edge/config.yaml')
    def config = steps.readYaml file: file_path

    install_edge_map.each { name, version ->
      String tenants = default_tenant.getId()
      String institutional = ""
      if (config[(name)].tenants) {
        config[(name)].tenants.each {
          def obj = [
            tenant  : it.tenant == "default" ? default_tenant.getId() : it.tenant,
            username: it.username,
            password: it.password == "default" ? default_tenant.getId() : it.password
          ]
          institutional += obj.tenant + "=" + obj.username + "," + obj.password + "\n"
          tenants += it.tenant == "default" ? "" : "," + it.tenant
        }
      }
      steps.writeFile file: "${name}-ephemeral-properties", text: """secureStore.type=Ephemeral
# a comma separated list of tenants
tenants=${tenants}
# a comma separated list of tenants mappings in form X-TO-CODE:tenant, where X-TO-CODE it's InnReach Header value
tenantsMappings=fli01:${default_tenant.getId()}
#######################################################
# For each tenant, the institutional user password...
#
# Note: this is intended for development purposes only
#######################################################
${default_tenant.getId()}=${default_user.getUsername()},${default_user.getPassword()}
${institutional}
"""
    }
  }

  void createEdgeUsers(OkapiTenant tenant, Map install_edge_map) {
    def file_path = tools.copyResourceFileToWorkspace('edge/config.yaml')
    def config = steps.readYaml file: file_path

    OkapiUser default_edge_user = new OkapiUser(
      username: tenant.getId(),
      password: tenant.getId(),
      firstName: "EDGE",
      lastName: "SYSTEM"
    )

    List permissions_list = []

    install_edge_map.each { name, version ->
      if (config[(name)].permissions) {
        permissions_list.addAll(config[(name)].permissions)
      }
      if (config[(name)].tenants) {
        config[(name)].tenants.each {
          if (it.tenant == "default" && it.create) {
            OkapiUser edge_user = new OkapiUser(
              username: it.username,
              password: it.password == "default" ? tenant.getId() : it.password,
              firstName: it.firstName,
              lastName: it.lastName,
              permissions: config[(name)].permissions
            )
            users.createUser(tenant, edge_user)
            auth.createUserCredentials(tenant, edge_user)
            permissions.createUserPermissions(tenant, edge_user)
          }
        }
      }
    }
    default_edge_user.setPermissions(permissions_list)
    if (default_edge_user.getPermissions()) {
      users.createUser(tenant, default_edge_user)
      auth.createUserCredentials(tenant, default_edge_user)
      permissions.createUserPermissions(tenant, default_edge_user)
    }
  }
}
