package org.folio.rest_v2.eureka

import hudson.util.Secret
import org.folio.Constants
import org.folio.models.EurekaModules
import org.folio.models.EurekaTenant
import org.folio.models.EurekaTenantConsortia
import org.folio.models.FolioModule
import org.folio.models.Role
import org.folio.models.User
import org.folio.rest_v2.eureka.kong.*
import org.folio.utilities.RequestException

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

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
          Indexes.get(kong).runIndex(tenant, index)
        }
      }
    }

    return this
  }

  /**
   * Get Configured Tenants on Environment Namespace.
   * @param moduleName Module Name to filter enabled applications.
   * @return Map of EurekaTenant objects.
   */
  Map<String, EurekaTenant> getExistedTenants(String moduleName) {
    /** Configured Tenants in Environment (namespace) */
    Map<String, EurekaTenant> configuredTenantsMap

    // Get configured Tenants from the Environment (namespace)
    configuredTenantsMap = Tenants.get(kong).getTenants().collectEntries { tenant -> [tenant.tenantName, tenant] }

    // Get enabled (entitled) applications for configured Tenants
    configuredTenantsMap.each { tenantName, tenant ->
      /** Enabled (entitled) applications for Tenant*/
      Map<String, String> enabledAppsMap = [:]

      // Get enabled applications from the Environment
      Tenants.get(kong).getEnabledApplications(tenant, "", true).each { appId, entitlement ->
        // Check if the module is enabled for the tenant
        if (entitlement.modules.find { moduleId -> moduleId.startsWith(moduleName) }) {
          // Save enabled application with the module to Map for processing
          enabledAppsMap.put(appId.split("-\\d+\\.\\d+\\.\\d+")[0], appId)
        }
      }

      if (enabledAppsMap.isEmpty()) {
        // Remove tenant without requested module from the configured tenants map
        configuredTenantsMap.remove(tenantName)
      }
      else {
        // Assign enabled applications to Tenant object
        tenant.applications = enabledAppsMap.clone() as Map
      }
    }

    return configuredTenantsMap
  }

  /**
   * Get Existed Applications on Environment Namespace.
   * @return Map of Application Name and Application ID.
   */
  static Map<String, String> getEnabledApplications(Map<String, EurekaTenant> tenants) {
    /** Enabled Applications in Environment */
    Map <String, String> enabledAppsMap = [:]

    // Get enabled applications from EurekaTenant List
    tenants.values().each {tenant ->
      tenant.applications.each {appName, appId ->
        enabledAppsMap.put(appName, appId)
      }
    }

    return enabledAppsMap
  }

  /**
   * Update Application Descriptor Flow.
   * @param applications Map of enabled applications in namespace.
   * @param modules EurekaModules object.
   * @param module FolioModule object.
   * @return Map<AppName, AppID> of updated applications.
   */
  Map<String, String> updateAppDescriptorFlow(Map<String, String> applications, EurekaModules modules, FolioModule module) {
    /** Enabled Application Descriptors Map */
    Map<String, Object> appDescriptorsMap = [:]

    //Get application descriptors for enabled applications in namespace
    applications.each { appName, appId ->
      def appDescriptor = Applications.get(kong).getRegisteredApplication(appId, true)
      if (appDescriptor['modules'].any { it['name'] == module.name }) {
        appDescriptorsMap.put(appId, appDescriptor)
      }
    }

    /** Updated Application Info, Map<AppName, AppID> */
    Map<String, String> updatedAppInfoMap = [:]

    // Init existing modules information with empty map
    modules.allModules = [:]

    // Get Application Descriptor Updated with New Module Version
    appDescriptorsMap.each { appId, descriptor->
      // Get Incremental Number for New Application Version
      String incrementalNumber = descriptor['version'].toString().tokenize('.').last().toInteger() + 2

      // Update existing Application Descriptor with New Module Version
      Map updatedAppDescriptor = getUpdatedApplicationDescriptor(descriptor as Map, module, incrementalNumber)

      // Print Updated Application Descriptor for Debugging
      //logger.info("Updated Application Descriptor to register:\n${prettyPrint(toJson(updatedAppDescriptor))}")

      // Register Updated Application Descriptor to Environment
      Applications.get(kong).registerApplication(updatedAppDescriptor)

      // Collect Updated Application information to Map<AppName, AppID>
      updatedAppInfoMap.put(updatedAppDescriptor['name'] as String, updatedAppDescriptor['id'] as String)

      // Collect Current Application Modules Information to EurekaModules Object in the Namespace
      modules.allModules.putAll(updatedAppDescriptor['modules'].collectEntries {[(it['name']), it['version']]} as Map)
    }

    return updatedAppInfoMap
  }

  /**
   * Get Updated Application Descriptor with new Module Version
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

    // Remove any URL links from previous module updates
    appDescriptor['modules'].each { it.containsKey('url') ? it.remove('url') : '' }

    // Update Application Descriptor with new Module Version
    for (item in appDescriptor['modules']) {
      if (item['name'] == module.name) {
        /** Module ID to update */
        String staleModuleId = item['id'] // save stale module id for descriptor removal

        // Update Module properties
        item['url'] = "${Constants.EUREKA_REGISTRY_URL}${module.name}-${module.version}"
        item['id'] = "${module.name}-${module.version}"
        item['version'] = module.version
        logger.info("Updated Module info:\n${item}")

        // Remove stale module descriptor from Updated Application Descriptor
        for (descriptor in appDescriptor['moduleDescriptors']) {
          if (descriptor['id'] == staleModuleId) {
            logger.info("Removing stale module descriptor \"${descriptor['id']}\" from Updated Application Descriptor")
            appDescriptor['moduleDescriptors'].remove(descriptor)
            break
          }
        }
        break
      }
    }

    logger.info("Updated Application Descriptor with new Module Version: ${module.name}-${module.version}\n")

    return appDescriptor as Map
  }

  /**
   * Run Module Discovery Flow.
   * @param module FolioModule object to discover
   */
  void runModuleDiscoveryFlow(FolioModule module) {
    try {
      logger.info("Check if ${module.name}-${module.version} module discovery exists...")
      Applications.get(kong).getModuleDiscovery(module)
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        Applications.get(kong).createModuleDiscovery(module)
      }
    }
  }

  /**
   * Enable Applications on Tenants Flow.
   * @param tenants Map of EurekaTenant objects.
   * @param appsToEnableMap Map<AppName, AppID> of registered application information.
   */
  void enableApplicationsOnTenantsFlow(Map<String, EurekaTenant> tenants, Map<String, String> appsToEnableMap) {
    tenants.each { tenantName, tenant ->
      // Upgrade Applications on Tenant
      Applications.get(kong).upgradeTenantApplication(tenant, appsToEnableMap)
    }
  }

  /**
   * Remove Stale Resources Flow.
   * @param applications Map of enabled applications in namespace.
   * @param updatedApplications Map of updated applications in namespace.
   * @param module FolioModule object.
   */
  void removeStaleResourcesFlow(Map<String, String> configuredApps, Map<String, String> updatedApplications, FolioModule module) {
    // Remove Previous Application Descriptor with Stale Module Version
    configuredApps.each { appName, appId ->
      if (updatedApplications.containsKey(appName)) {

        // Get Previous Module Version Discovery removed
        Applications.get(kong).searchModuleDiscovery("name==${module.name}")['discovery']?.each { moduleDiscovery ->
          if (moduleDiscovery['id'] != module.id) { // Remove only for the previous module versions
            Applications.get(kong).deleteModuleDiscovery(moduleDiscovery['id'] as String)
          }
        }

        // Delete Application Descriptor
        Applications.get(kong).deleteRegisteredApplication(appId)

      }
    }
  }

  /**
   * Remove Resources on Fail Flow.
   * @param updatedApplications Map of updated applications in namespace.
   * @param module FolioModule object.
   */
  void removeResourcesOnFailFlow(Map<String, String> updatedApplications, FolioModule module) {
    if (updatedApplications.isEmpty()) {
      logger.info("No updated applications found to remove resources.")
    }
    else {
      logger.info("Removing resources for failed module update...")

      // Remove Updated Application Descriptor with New Module Version
      updatedApplications.each { appName, appId ->
        // Get Updated Module Discovery removed
        Applications.get(kong).searchModuleDiscovery("name==${module.name}")['discovery']?.each { moduleDiscovery ->
          if (moduleDiscovery['id'] == module.id) { // Remove only for the updated module versions
            Applications.get(kong).deleteModuleDiscovery(moduleDiscovery['id'] as String)
          }
        }

        // Delete Application Descriptor
        Applications.get(kong).deleteRegisteredApplication(appId)

        logger.info("Removed resources for failed module update: ${appName}")
      }
    }
  }
}
