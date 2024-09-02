package org.folio.rest_v2.eureka

import org.folio.models.EurekaModules
import org.folio.models.EurekaTenant
import org.folio.models.EurekaTenantConsortia
import org.folio.models.Role
import org.folio.models.User
import org.folio.rest_v2.eureka.kong.*

class Eureka extends Base {

  static List<String> CURRENT_APPLICATIONS = [
    "app-platform-full"
    , "app-consortia"
  ]

  static List<String> CURRENT_APPLICATIONS_WO_CONSORTIA = [
    "app-platform-full"
  ]

  private Kong kong

  Eureka(def context, String kongUrl, String keycloakUrl, boolean debug = false) {
    this(new Kong(context, kongUrl, keycloakUrl, debug))
  }

  Eureka(Kong kong) {
    super(kong.context, kong.getDebug())

    this.kong = kong
  }

  Eureka createTenantFlow(EurekaTenant tenant) {
    EurekaTenant createdTenant = Tenants.get(kong).createTenant(tenant)

    tenant.withUUID(createdTenant.getUuid())
      .withClientSecret(createdTenant.getClientSecret())

    Tenants.get(kong).enableApplicationsOnTenant(tenant)

    //create tenant admin user
    createUserFlow(tenant, tenant.adminUser
      , new Role(name: "adminRole", desc: "Admin role")
      , Permissions.get(kong).getCapabilitiesId(tenant)
      , Permissions.get(kong).getCapabilitySetsId(tenant))

    return this
  }

  Eureka createUserFlow(EurekaTenant tenant, User user, Role role, List<String> permissions, List<String> permissionSets) {
    user.patronGroup.setUuid(
      UserGroups.get(kong)
        .createUserGroup(tenant, user.patronGroup)
        .getUuid()
    )

    user.setUuid(
      Users.get(kong)
        .createUser(tenant, user)
        .getUuid()
    )

    Users.get(kong).setUpdatePassword(tenant, user)

    role.setUuid(
      Permissions.get(kong)
        .createRole(tenant, role)
        .getUuid()
    )

    Permissions.get(kong).assignCapabilitiesToRole(tenant, role, permissions)
      .assignCapabilitySetsToRole(tenant, role, permissionSets)
      .assignRolesToUser(tenant, user, [role])

    return this
  }

  Map<String, String> registerApplications(List<String> appNames, Map<String, String> moduleList){
    Map<String, String> apps = [:]

    appNames.each {appName ->
      def jsonAppDescriptor = context.folioEurekaAppGenerator.generateApplicationDescriptor(appName, moduleList, getDebug())

      apps.put(appName, Applications.get(kong).registerApplication(jsonAppDescriptor))
    }

    return apps
  }

  Eureka assignAppToTenants(List<EurekaTenant> tenants, Map<String, String> registeredApps){
    tenants.each {tenant ->
      tenant.applications = registeredApps.clone() as Map

      if(!tenant instanceof EurekaTenantConsortia)
        tenant.applications.remove("app-consortia")
    }

    return this
  }

  Map<String, String> registerApplicationsFlow(List<String> appNames
                                               , EurekaModules modules
                                               , List<EurekaTenant> tenants){

    Map<String, String> registeredApps = registerApplications(appNames, modules.getAllModules())

    assignAppToTenants(tenants, registeredApps)

    return registeredApps
  }

  Eureka registerModulesFlow(EurekaModules modules, Map<String, String> apps, List<EurekaTenant> tenants = null){
    updateRegisteredModules(modules, apps)

    if(tenants)
      tenants.each {tenant -> updateTenantRegisteredModules(tenant, apps)}

    Applications.get(kong).registerModules(
      [
        "discovery": modules.getDiscoveryList()
      ]
    )

    return this
  }

  Eureka updateTenantRegisteredModules(EurekaTenant tenant, Map<String, String> apps){
    updateRegisteredModules(tenant.getModules(), apps)

    return this
  }

  Eureka updateRegisteredModules(EurekaModules modules, Map<String, String> apps){
    List restrictionList = []
    apps.values().each {appId ->
      Applications.get(kong).getRegisteredApplication(appId).modules.each{ module ->
        if(!restrictionList.contains(module.id))
          restrictionList.add(module.id)
      }
    }

    modules.updateDiscoveryList(restrictionList)

    return this
  }

  /**
   * Sets up a consortia with the given tenants.
   *
   * @param consortiaTenants A map of consortia tenants.
   */
  Eureka setUpConsortiaFlow(List<EurekaTenantConsortia> consortiaTenants) {
    EurekaTenantConsortia centralConsortiaTenant =
            consortiaTenants.find { it.isCentralConsortiaTenant }

    Consortia.get(kong).createConsortia(centralConsortiaTenant)

    Consortia.get(kong)
      .addCentralConsortiaTenant(centralConsortiaTenant)
      .checkConsortiaStatus(centralConsortiaTenant, centralConsortiaTenant)

    consortiaTenants.findAll { (!it.isCentralConsortiaTenant) }
      .each { institutionalTenant ->
        Consortia.get(kong)
          .addConsortiaTenant(centralConsortiaTenant, institutionalTenant)
          .checkConsortiaStatus(centralConsortiaTenant, institutionalTenant)
      }

    return this
  }

  Eureka initializeFromScratch(Map<String, EurekaTenant> tenants, boolean enableConsortia) {
    tenants.each { tenantId, tenant -> createTenantFlow(tenant) }

    if (enableConsortia)
      setUpConsortiaFlow(
        tenants.values().findAll {
          it instanceof EurekaTenantConsortia
        } as List<EurekaTenantConsortia>
      )

    tenants.each { tenantId, tenant ->
      if (tenant.indexes) {
        tenant.indexes.each { index ->
          Indexes.get(kong).runIndex(tenant, index)
        }
      }
    }

    return this
  }
}
