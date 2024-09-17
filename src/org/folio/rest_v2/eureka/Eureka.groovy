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
import org.folio.utilities.RequestException

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
   * Get Configured Tenants on Environment Namespace.
   * @return Map of EurekaTenant objects.
   */
  Map<String, EurekaTenant> getExistedTenants() {
    /** Configured Tenants in Environment (namespace) */
    Map<String, EurekaTenant> configuredTenantsMap

    // Get configured Tenants from the Environment (namespace)
    configuredTenantsMap = Tenants.get(kong).getTenants().collectEntries { tenant -> [tenant.tenantName, tenant] }

    // Get enabled (entitled) applications for configured Tenants
    configuredTenantsMap.each { tenantName, tenant ->
      /** Enabled (entitled) applications for Tenant*/
      Map<String, String> enabledAppsMap = [:]

      // Get enabled applications from the Environment
      Tenants.get(kong).getEnabledApplications(tenant).each { appId, app ->
        enabledAppsMap.put(appId.split("-\\d+\\.\\d+\\.\\d+")[0], appId)
      }

      // Assign enabled applications to Tenant object
      tenant.applications = enabledAppsMap.clone() as Map
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

    // Get enabled applications from EurekaTenant List of objects
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
   * @param module FolioModule object.
   * @return Map of EurekaTenant objects.
   */
  Eureka updateAppDescriptorFlow(Map<String, String> applications, FolioModule module) {
    /** Enabled Application Descriptors Map */
    Map<String, Object> appDescriptorsMap = [:]

    //Get application descriptors for enabled applications in namespace
    applications.each { appName, appId ->
      def appDescriptor = Applications.get(kong).getRegisteredApplication(appId)
      if (appDescriptor['modules'].any { it['name'] == module.name }) {
        appDescriptorsMap.put(appId, appDescriptor)
      }
    }

    // Get Application Descriptor Updated with New Module Version
    appDescriptorsMap.each { appId, descriptor->
      // Get Incremental Number for New Application Version
      String incrementalNumber = descriptor['version'].toString().tokenize('.').last().toInteger() + 1

      // Update existing Application Descriptor with New Module Version
      Map updatedAppDescriptor = getUpdatedApplicationDescriptor(descriptor as Map, module, incrementalNumber)

      // Register Updated Application Descriptor to Environment
      Applications.get(kong).registerApplication(updatedAppDescriptor)
    }

    return this
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

    // Update Application Descriptor with new Module Version
    appDescriptor['modules'].findAll { it['name'] == module.name }.each {
      it['url'] = "${Constants.EUREKA_REGISTRY_URL}${module.name}-${module.version}"
      it['id'] = "${module.name}-${module.version}"
      it['version'] = module.version
    }

    // TODO: RANCHER-1700 - get rid off related workaround code once pipeline testing is done
    // Update Application Descriptor for "mod-scheduler-1.3.0-SNAPSHOT.81"
    appDescriptor['modules'].findAll { it['name'] == "mod-scheduler-1.3.0-SNAPSHOT.81" }.each {
      it['url'] = "https://folio-registry.dev.folio.org/_/proxy/modules/mod-scheduler-1.3.0-SNAPSHOT.81"
    }

    logger.info("Updated Application Descriptor with new Module Version: ${module.name}-${module.version}\n${appDescriptor}")

    return appDescriptor as Map
  }

  /**
   * Run Module Discovery Flow.
   * @param module FolioModule object to discover
   */
  void runModuleDiscoveryFlow(FolioModule module) {
    try {
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
   */
  void enableApplicationsOnTenantsFlow(Map<String, EurekaTenant> tenants) {
    tenants.each { tenantName, tenant ->
      if(!(tenant instanceof EurekaTenantConsortia))
        tenant.applications.remove("app-consortia")

      // Enable Applications on Tenant
      Tenants.get(kong).enableApplicationsOnTenant(tenant)
    }
  }
}
