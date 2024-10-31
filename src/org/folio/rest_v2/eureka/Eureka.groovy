package org.folio.rest_v2.eureka

import hudson.util.Secret
import org.folio.Constants
import org.folio.models.EurekaModules
import org.folio.models.EurekaTenant
import org.folio.models.EurekaTenantConsortia
import org.folio.models.Role
import org.folio.models.User
import org.folio.rest_v2.eureka.kong.*

class Eureka extends Base {

  static Map<String, String> CURRENT_APPLICATIONS = [
    "app-platform-full": "snapshot"
    , "app-consortia": "snapshot"
  ]

  static Map<String, String> CURRENT_APPLICATIONS_WO_CONSORTIA = [
    "app-platform-full": "snapshot"
  ]

  private Kong kong

  Eureka(def context, String kongUrl, String keycloakUrl, boolean debug = false) {
    this(new Kong(context, kongUrl, keycloakUrl, debug))
  }

  Eureka(Kong kong) {
    super(kong.context, kong.getDebug())

    this.kong = kong
  }

  Eureka defineKeycloakTTL(int ttl = 3600) {
    kong.keycloak.defineTTL("master", ttl)

    return this
  }

  Eureka createTenantFlow(EurekaTenant tenant, String cluster, String namespace) {
    EurekaTenant createdTenant = Tenants.get(kong).createTenant(tenant)

    tenant.withUUID(createdTenant.getUuid())
      .withClientSecret(retrieveTenantClientSecretFromAWSSSM(tenant))

    kong.keycloak.defineTTL(tenant.tenantId, 3600)

    Tenants.get(kong).enableApplicationsOnTenant(tenant)

    context.folioTools.stsKafkaLag(cluster, namespace, tenant.tenantId)

    //create tenant admin user
    createUserFlow(tenant, tenant.adminUser
      , new Role(name: "adminRole", desc: "Admin role")
      , Permissions.get(kong).getCapabilitiesId(tenant)
      , Permissions.get(kong).getCapabilitySetsId(tenant))

    return this
  }

  /**
   * Retrieve Client Secret for the Tenant from AWS SSM parameter
   * @param EurekaTenant object
   * @return client secret as Secret object
   */
  Secret retrieveTenantClientSecretFromAWSSSM(EurekaTenant tenant){
    context.awscli.withAwsClient {
      return Secret.fromString(
              context.awscli.getSsmParameterValue(Constants.AWS_REGION, tenant.secretStoragePathName, true) as String
      )
    }
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

    Permissions.get(kong).assignCapabilitiesToRole(tenant, role, permissions, true)
      .assignCapabilitySetsToRole(tenant, role, permissionSets, true)
      .assignRolesToUser(tenant, user, [role])

    Users.get(kong).getAndAssignSPs(tenant, user)

    return this
  }

  Map<String, String> registerApplications(Map<String, String> appNames, Map<String, String> moduleList){
    Map<String, String> apps = [:]

    appNames.each {appName, appBranch ->
      def jsonAppDescriptor = context.folioEurekaAppGenerator.generateApplicationDescriptor(appName, moduleList, appBranch, getDebug())

      apps.put(appName, Applications.get(kong).registerApplication(jsonAppDescriptor))
    }

    return apps
  }

  Eureka assignAppToTenants(List<EurekaTenant> tenants, Map<String, String> registeredApps){
    tenants.each {tenant ->
      tenant.applications = registeredApps.clone() as Map

      if(!(tenant instanceof EurekaTenantConsortia))
        tenant.applications.remove("app-consortia")
    }

    return this
  }

  Map<String, String> registerApplicationsFlow(Map<String, String> appNames
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

  Eureka initializeFromScratch(Map<String, EurekaTenant> tenants, String cluster, String namespace, boolean enableConsortia) {
    tenants.each { tenantId, tenant -> createTenantFlow(tenant, cluster, namespace) }

    if (enableConsortia)
      setUpConsortiaFlow(
        tenants.values().findAll {
          it instanceof EurekaTenantConsortia
        } as List<EurekaTenantConsortia>
      )

    tenants.each { tenantId, tenant ->
      if (tenant.indexes) {
        tenant.indexes.each { index ->
          Indexes.get(kong).runIndexFlow(tenant, index)
        }
      }
    }

    return this
  }
}
