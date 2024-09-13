package org.folio.rest_v2.eureka

import hudson.util.Secret
import org.folio.Constants
import org.folio.models.EurekaModules
import org.folio.models.EurekaNamespace
import org.folio.models.EurekaTenant
import org.folio.models.EurekaTenantConsortia
import org.folio.models.FolioModule
import org.folio.models.Role
import org.folio.models.User
import org.folio.rest_v2.eureka.kong.*

class Eureka extends Base {

  static Map<String, String> CURRENT_APPLICATIONS = [
    "app-platform-full": "snapshot"
    , "app-consortia": "master"
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

  Eureka createTenantFlow(EurekaTenant tenant) {
    EurekaTenant createdTenant = Tenants.get(kong).createTenant(tenant)

    tenant.withUUID(createdTenant.getUuid())
      .withClientSecret(retrieveTenantClientSecretFromAWSSSM(tenant))

    kong.keycloak.defineTTL(tenant.tenantId, 3600)

    Tenants.get(kong).enableApplicationsOnTenant(tenant)

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

  /**
   * Update Application Descriptor Flow.
   *
   * @param namespace EurekaNamespace object.
   * @param tenantsList List of Tenant Names.
   * @return The current Eureka object.
   */
  Eureka updateAppDescriptorFlow(FolioModule module, List<String> tenantsList) {
    // Get specified Tenants from the Environment (namespace)
    List<EurekaTenant> tenants = Tenants.get(kong).getTenants().findAll { tenant ->
      tenantsList.contains(tenant.tenantName)
    }

    /** Enabled (entitled) applications Map */
    Map<String, List<Map>> enabledAppsMap = [:]

    // Get enabled applications with specified module for requested Tenants
    tenants.each { tenant ->
      Tenants.get(kong).getEnabledApplicationsWithModule(tenant, module).each { key, value ->
        enabledAppsMap.containsKey(key) ? enabledAppsMap[key].add(value) : enabledAppsMap.put(key, [value])
      }
    }

    logger.debug("Enabled applications with tenants: ${enabledAppsMap}")

    /** Enabled Application Descriptors Map */
    Map appDescriptorsMap = [:]

    //Get application descriptors for enabled applications
    enabledAppsMap.keySet().each { appId ->
      appDescriptorsMap.put(appId, Applications.get(kong).getRegisteredApplication(appId))
    }

    logger.debug("Application Descriptors for enabled applications: ${appDescriptorsMap}")

    // Get Application Descriptor Updated with New Module Version
    enabledAppsMap.keySet().each { appId->
      def appDescriptor = appDescriptorsMap[appId]
      String incrementalNumber = appDescriptor.version.tokenize('.').last().toInteger() + 1

      // Update existing Application Descriptor with New Module Version
      Map updatedAppDescriptor = getUpdatedApplicationDescriptor(appDescriptor as Map, module, incrementalNumber)

      // Put back Updated Application Descriptor to Environment
      Applications.get(kong).registerApplication(updatedAppDescriptor)
    }

    return this
  }

  /**
   * Get Updated Application Descriptor with new Module Version
   *
   * @param appDescriptor Current Application Descriptor as a Map
   * @param module Module object to be updated
   * @param buildNumber Build Number for new Application Version
   * @return Updated Application Descriptor as a Map
   */
  Map getUpdatedApplicationDescriptor(Map appDescriptor, FolioModule module, String buildNumber) {
    // Update Application Descriptor with incremented Application Version
    String currentAppVersion = appDescriptor.version
    String newAppVersion = currentAppVersion.replaceFirst(/SNAPSHOT\.\d+/, "SNAPSHOT.${buildNumber}")
    appDescriptor.version = newAppVersion
    appDescriptor.id = "${appDescriptor.name}-${newAppVersion}"

    // Update Application Descriptor with new Module Version
    appDescriptor.modules.findAll { it.name == module.name }.each {
      it.url = "${Constants.EUREKA_REGISTRY_URL}${module.name}-${module.version}"
      it.id = "${module.name}-${module.version}"
      it.version = module.version
    }

    // Remove broken module version from the Application Descriptor
    appDescriptor.modules.each { mod ->
      if(mod.id.contains("mod-roles-keycloak-1.4.5-SNAPSHOT.121")) { mod.remove() }
    }

    logger.info("Updated Application Descriptor with new Module Version: ${module.name}-${module.version}")

    return appDescriptor as Map
  }
}
