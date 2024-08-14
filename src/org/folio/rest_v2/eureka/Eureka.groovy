package org.folio.rest_v2.eureka

import org.folio.models.OkapiTenant
import org.folio.models.Role
import org.folio.models.Tenant
import org.folio.models.User
import org.folio.rest_v2.eureka.kong.Permissions
import org.folio.rest_v2.eureka.kong.Tenants
import org.folio.rest_v2.eureka.kong.UserGroups
import org.folio.rest_v2.eureka.kong.Users

class Eureka extends Base {

  private Kong kong

  Eureka(def context, String kongUrl, String keycloakUrl, boolean debug = false) {
    super(context, debug)

    this.kong = Kong.get(context, kongUrl, keycloakUrl, debug)
  }

  Eureka(Kong kong, boolean debug = false) {
    super(kong.context, debug)

    this.kong = kong
  }

  void createTenantFlow(OkapiTenant tenant, List<String> applications) {
    Tenant eurekaTenant = (kong as Tenants).createTenant(tenant)

    (kong as Tenants).enableApplicationsOnTenant(eurekaTenant, applications)

    //create tenant admin user
    createUserFlow(eurekaTenant, tenant.adminUser
      , new Role(name: "adminRole", desc: "Admin role")
      , (kong as Permissions).getCapabilitiesId(tenant)
      , (kong as Permissions).getCapabilitySetsId(tenant))
  }

  void createUserFlow(Tenant tenant, User user, Role role, List<String> permissions, List<String> permissionSets) {
    user.patronGroup = (kong as UserGroups).createUserGroup(tenant, user.patronGroup)
    user = (kong as Users).createUser(tenant, user)
    (kong as Users).setUpdatePassword(tenant, user)

    role = (kong as Permissions).createRole(tenant, role)
    (kong as Permissions).assignCapabilitiesToRole(tenant, role, permissions)
      .assignCapabilitySetsToRole(tenant, role, permissionSets)
      .assignRolesToUser(tenant, user, [role])
  }

  void initializeFromScratch(Map<String, OkapiTenant> tenants, boolean enableConsortia) {
    tenants.each { tenantId, tenant ->
      createTenantFlow(tenant,
        [
          "app-platform-minimal-1.0.0-SNAPSHOT.38"
          , "app-platform-complete-1.0.0-SNAPSHOT.53"
        ]
      )
    }
  }
}
