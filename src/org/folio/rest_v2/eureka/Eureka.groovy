package org.folio.rest_v2.eureka

import org.folio.models.OkapiTenant
import org.folio.models.Role
import org.folio.models.Tenant
import org.folio.models.User
import org.folio.rest_v2.eureka.kong.Permissions
import org.folio.rest_v2.eureka.kong.Tenants
import org.folio.rest_v2.eureka.kong.UserGroups
import org.folio.rest_v2.eureka.kong.Users
import org.folio.utilities.Logger
import org.folio.utilities.RestClient
import org.folio.utilities.Tools

class Eureka extends Base {

  private Kong kong

  Eureka(def context, String kongUrl, String keycloakUrl, boolean debug = false) {
//    super(context, debug)

    context.println("I'm in Eureka constructor")

    this.context = context
    this.logger = new Logger(context, this.getClass().getCanonicalName())
    this.tools = new Tools(context)
    this.restClient = new RestClient(context, debug)

    this.kong = new Kong(context, kongUrl, keycloakUrl, debug)
  }

  Eureka(Kong kong) {
    super(kong.context, kong.restClient.debug)

    this.kong = kong
  }

  void createTenantFlow(OkapiTenant tenant, List<String> applications) {
    logger.debug("I am in Eureka.createTenantFlow")
    Tenant eurekaTenant = Tenants.get(kong).createTenant(tenant)

    Tenants.get(kong).enableApplicationsOnTenant(eurekaTenant, applications)

    //create tenant admin user
    createUserFlow(eurekaTenant, tenant.adminUser
      , new Role(name: "adminRole", desc: "Admin role")
      , Permissions.get(kong).getCapabilitiesId(tenant)
      , Permissions.get(kong).getCapabilitySetsId(tenant))
  }

  void createUserFlow(Tenant tenant, User user, Role role, List<String> permissions, List<String> permissionSets) {
    user.patronGroup = UserGroups.get(kong).createUserGroup(tenant, user.patronGroup)
    user = Users.get(kong).createUser(tenant, user)
    Users.get(kong).setUpdatePassword(tenant, user)

    role = Permissions.get(kong).createRole(tenant, role)
    Permissions.get(kong).assignCapabilitiesToRole(tenant, role, permissions)
      .assignCapabilitySetsToRole(tenant, role, permissionSets)
      .assignRolesToUser(tenant, user, [role])
  }

  void initializeFromScratch(Map<String, OkapiTenant> tenants, boolean enableConsortia) {
    tenants.each { tenantId, tenant ->
      createTenantFlow(tenant,
        [
          "app-platform-full-1.0.0-SNAPSHOT.175"
          , "app-consortia-1.0.0-SNAPSHOT.176"
        ]
      )
    }
  }
}
