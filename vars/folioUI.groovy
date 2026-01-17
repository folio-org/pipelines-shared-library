#!groovy
import com.cloudbees.groovy.cps.NonCPS
import groovy.text.StreamingTemplateEngine
import org.folio.Constants
import org.folio.jenkins.PodTemplates
import org.folio.models.OkapiTenant
import org.folio.models.RancherNamespace
import org.folio.models.TenantUi
import org.folio.models.module.FolioModule
import org.folio.utilities.RestClient

void build(String okapiUrl, OkapiTenant tenant, boolean isEureka = false, String kongDomain = ''
           , String keycloakDomain = '', boolean enableEcsRequests = false, boolean singleConsortiaUI = false) {
  TenantUi tenantUi = tenant.getTenantUi()
  PodTemplates podTemplates = new PodTemplates(this)

  podTemplates.stripesAgent {
    stage('[UI] Checkout') {
      cleanWs()

      checkout([$class           : 'GitSCM',
                branches         : [[name: tenantUi.getHash()]],
                extensions       : [[$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true]],
                userRemoteConfigs: [[credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID,
                                     url          : "${Constants.FOLIO_GITHUB_URL}/platform-complete.git"]]])
    }

    stage('[UI] Add folio extensions') {
      if (tenantUi.getCustomUiModules() || isEureka) {
        if (isEureka) {
          okapiUrl = _handleEurekaConfiguration(tenant, tenantUi, kongDomain, keycloakDomain, enableEcsRequests, singleConsortiaUI)
        }

        List uiModulesToAdd = _updatePackageJsonFile(tenantUi)
        _updateStripesConfigJsFile(uiModulesToAdd)
      }
    }

    stage('[UI] Build and Push') {
      container('kaniko') {
        withAWS(credentials: Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID, region: Constants.AWS_REGION) {
          ecrLogin()
          folioKaniko.dockerHubLogin()
          // Add YARN_CACHE_FOLDER to the Dockerfile
          sh "sed -i '/^FROM /a ENV YARN_CACHE_FOLDER=${WORKSPACE}/.cache/yarn' docker/Dockerfile"
          // Build and push the image
          sh """/kaniko/executor --destination ${tenantUi.getImageName()} \
--build-arg OKAPI_URL=${okapiUrl} \
--build-arg TENANT_ID=${tenant.getTenantId()} \
--dockerfile docker/Dockerfile --context ."""
        }
      }
    }

    stage('Update keycloak redirect') {
      if (isEureka) {
        _updateKeycloakRedirects(keycloakDomain, tenant, tenantUi, kongDomain, singleConsortiaUI)
      }
    }
  }
           }

void deploy(RancherNamespace namespace, OkapiTenant tenant) {
  PodTemplates podTemplates = new PodTemplates(this)

  podTemplates.rancherAgent {
    stage('[UI] Deploy bundle') {
      TenantUi tenantUi = tenant.getTenantUi()
      def clusterName = namespace.getClusterName()
      def tenantId = tenantUi.getTenantId()
      def tag = tenantUi.getTag()
      folioHelm.withKubeConfig(clusterName) {
        folioHelm.deployFolioModule(namespace, 'ui-bundle', tag, false, tenantId)
      }
    }
  }
}

void buildAndDeploy(RancherNamespace namespace, OkapiTenant tenant, boolean isEureka = false, String kongDomain = ''
                    , String keycloakDomain = '', boolean enableEcsRequests = false, boolean singleConsortiaUI = false) {
  build("https://${namespace.getDomains()['okapi']}", tenant, isEureka, kongDomain, keycloakDomain, enableEcsRequests, singleConsortiaUI)
  deploy(namespace, tenant)
}

private void _updateStripesConfigJsFile(List<String> uiModulesToAdd) {
  final String stripesConfigFile = 'stripes.config.js'
  String fileContent = readFile(file: stripesConfigFile)

  uiModulesToAdd.each { moduleName ->
    // Check if the module already exists
    if (!fileContent.contains(moduleName)) {
      // Add the missing module to the modules section
      String moduleInsertion = "    '${moduleName}' : {},"
      fileContent = fileContent.replaceFirst(/(modules\s*:\s*\{)/, "\$1\n$moduleInsertion")
      echo "Module ${moduleName} added successfully!"
    } else {
      echo "Module '${moduleName}' already exists."
      }
    }

  // Ensure that changes are written back to the file
  try {
    writeFile(file: stripesConfigFile, text: fileContent)
  } catch (Exception e) {
    echo "Error writing to file: ${e.message}"
    throw e
  }
}

/**
 * Handles Eureka-specific configuration setup including template copying,
 * binding creation, and custom UI module configuration.
 *
 * @param tenant The OkapiTenant configuration
 * @param tenantUi The TenantUi configuration
 * @param kongDomain Kong gateway domain
 * @param keycloakDomain Keycloak domain
 * @param enableEcsRequests Enable ECS-specific requests
 * @param singleConsortiaUI Use single consortia UI configuration
 * @return The updated Okapi URL for the environment
 */
private String _handleEurekaConfiguration(OkapiTenant tenant, TenantUi tenantUi, String kongDomain,
                                          String keycloakDomain, boolean enableEcsRequests, boolean singleConsortiaUI) {
  String tenantId = tenant.getTenantId()
  String okapiUrl = "https://${kongDomain}"

  // Copy Eureka template files
  sh 'cp -R -f eureka-tpl/* .'

  // Build tenant options configuration
  String tenantOptionsJson = buildTenantOptionsJson(tenant, singleConsortiaUI)

  // Create base binding configuration
  Map binding = [
    kongUrl          : "https://${kongDomain}",
    tenantUrl        : "https://${tenantUi.getDomain()}",
    keycloakUrl      : "https://${keycloakDomain}",
    hasAllPerms      : false,
    isSingleTenant   : true,
    tenantOptions    : tenantOptionsJson,
    enableEcsRequests: enableEcsRequests
  ]

  // Apply consortia-specific configuration if needed
  if (tenantUi.getCustomUiModules()*.name.contains('folio_consortia-settings')) {
    binding.kongUrl = "https://ecs-${kongDomain}"
    binding.isSingleTenant = false
    okapiUrl = binding.kongUrl
  }

  // Apply tenant-specific URL overrides
  okapiUrl = _applyTenantSpecificUrlOverrides(tenantId, kongDomain, binding, okapiUrl)

  // Generate and write stripes configuration
  writeFile(file: 'stripes.config.js',
    text: make_tpl(readFile(file: 'stripes.config.js', encoding: 'UTF-8') as String, binding),
    encoding: 'UTF-8')

  archiveArtifacts artifacts: 'stripes.config.js', allowEmptyArchive: false

  // Add Eureka-specific UI modules
  _addEurekaCustomUiModules(tenantUi)

  return okapiUrl
}

/**
 * Applies tenant-specific URL overrides for special tenant configurations.
 *
 * @param tenantId The tenant identifier
 * @param kongDomain Kong gateway domain
 * @param binding The configuration binding map to update
 * @param currentOkapiUrl The current Okapi URL
 * @return The updated Okapi URL
 */
private String _applyTenantSpecificUrlOverrides(String tenantId, String kongDomain, Map binding, String currentOkapiUrl) {
  String okapiUrl = currentOkapiUrl

  switch (tenantId) {
    case 'fs09000002':
      binding.kongUrl = "https://fs02-${kongDomain}"
      binding.isSingleTenant = true
      okapiUrl = binding.kongUrl
      break
    case 'fs09000003':
      binding.kongUrl = "https://fs03-${kongDomain}"
      binding.isSingleTenant = true
      okapiUrl = binding.kongUrl
      break
    case 'consortium2':
      binding.kongUrl = "https://ecs2-${kongDomain}"
      binding.isSingleTenant = false
      okapiUrl = binding.kongUrl
      break
  }

  return okapiUrl
}

/**
 * Adds Eureka-specific custom UI modules to the tenant configuration.
 *
 * @param tenantUi The TenantUi configuration to update
 */
private void _addEurekaCustomUiModules(TenantUi tenantUi) {
  List eurekaCustomUiModules = ['folio_authorization-policies',
                                'folio_authorization-roles',
                                'folio_plugin-select-application']
  eurekaCustomUiModules.each { moduleName ->
    FolioModule uiModule = new FolioModule()
    uiModule.setName(moduleName)
    uiModule.setVersion('>=1.0.0')
    tenantUi.customUiModules.add(uiModule)
  }
}

/**
 * Updates Keycloak redirect URIs and client configurations for Eureka environments.
 * Handles both single tenants and consortia tenant structures.
 *
 * @param keycloakDomain Keycloak domain
 * @param tenant The OkapiTenant configuration
 * @param tenantUi The TenantUi configuration
 * @param kongDomain Kong gateway domain
 * @param singleConsortiaUI Use single consortia UI configuration
 */
private void _updateKeycloakRedirects(String keycloakDomain, OkapiTenant tenant, TenantUi tenantUi, String kongDomain, boolean singleConsortiaUI) {
  String tenantId = tenant.getTenantId()
  RestClient client = new RestClient(this, true)

  // Authenticate with Keycloak master realm
  String token = _authenticateWithKeycloak(client, keycloakDomain)
  Map headers = ['Authorization': "Bearer ${token}"]

  // Determine which tenants need Keycloak configuration updates
  List<String> tenantsToUpdate = _determineTenantsToUpdate(tenantId)

  // Update Keycloak configuration for each tenant
  tenantsToUpdate.each { currentTenantId ->
    _updateKeycloakClientConfiguration(client, headers, keycloakDomain, currentTenantId, tenantId, tenantUi, kongDomain, singleConsortiaUI)
  }
}

/**
 * Authenticates with Keycloak master realm and returns access token.
 *
 * @param client RestClient instance
 * @param keycloakDomain Keycloak domain
 * @return Access token for Keycloak API calls
 */
private String _authenticateWithKeycloak(RestClient client, String keycloakDomain) {
  Map headers = ['Content-Type': 'application/x-www-form-urlencoded']
  String tokenUrl = "https://${keycloakDomain}/realms/master/protocol/openid-connect/token"
  String tokenBody = 'grant_type=password&username=admin&password=SecretPassword&client_id=admin-cli'

  def response = client.post(tokenUrl, tokenBody, headers).body
  return response['access_token']
}

/**
 * Determines which tenants need Keycloak configuration updates based on consortia structure.
 *
 * @param tenantId The primary tenant identifier
 * @return List of tenant IDs that need configuration updates
 */
private List<String> _determineTenantsToUpdate(String tenantId) {
  List<String> tenantsToUpdate = []

  switch (tenantId) {
    case 'consortium':
      // Central tenant + member tenants
      tenantsToUpdate = [tenantId, 'university', 'college']
      break
    case 'consortium2':
      // Central tenant + member tenants
      tenantsToUpdate = [tenantId, 'university2', 'college2']
      break
    case 'cs00000int':
      // Central tenant + all institutional member tenants
      tenantsToUpdate = [tenantId]
      (1..11).each { num ->
        tenantsToUpdate << "cs00000int_${String.format('%04d', num)}"
      }
      break
    default:
      // Single tenant (non-central or member tenant)
      tenantsToUpdate = [tenantId]
      break
  }

  return tenantsToUpdate
}

/**
 * Updates Keycloak client configuration for a specific tenant.
 *
 * @param client RestClient instance
 * @param headers HTTP headers with authorization
 * @param keycloakDomain Keycloak domain
 * @param currentTenantId Tenant ID being updated
 * @param originalTenantId Original tenant ID for URL generation
 * @param tenantUi TenantUi configuration
 * @param kongDomain Kong gateway domain
 * @param singleConsortiaUI Use single consortia UI configuration
 */
private void _updateKeycloakClientConfiguration(RestClient client, Map headers, String keycloakDomain,
                                                String currentTenantId, String originalTenantId, TenantUi tenantUi, String kongDomain, boolean singleConsortiaUI) {
  String getRealmUrl = "https://${keycloakDomain}/admin/realms/${currentTenantId}/clients?clientId=${currentTenantId}-application"
  def realm = client.get(getRealmUrl, headers).body

  if (realm && !realm.isEmpty()) {
    String updateRealmUrl = "https://${keycloakDomain}/admin/realms/${currentTenantId}/clients/${realm['id'].get(0)}"
    headers['Content-Type'] = 'application/json'

    String baseDomain = tenantUi.getDomain()
    String currentTenantUrl = singleConsortiaUI ?
      "https://${baseDomain.replace(originalTenantId, currentTenantId)}" :
      "https://${baseDomain}"

    // Build redirect URIs list
    List<String> redirectUrisList = _buildRedirectUrisList(originalTenantId, kongDomain, currentTenantUrl, currentTenantId)

    // Create update content
    def updateContent = [
      rootUrl                     : currentTenantUrl,
      baseUrl                     : currentTenantUrl,
      adminUrl                    : currentTenantUrl,
      redirectUris                : redirectUrisList,
      webOrigins                  : ['/*'],
      authorizationServicesEnabled: true,
      serviceAccountsEnabled      : true,
      attributes                  : ['post.logout.redirect.uris': "/*##${currentTenantUrl}/*", login_theme: 'custom-theme']
    ]

    def ssoUpdates = [
      resetPasswordAllowed: true
    ]

    // Apply updates
    client.put(updateRealmUrl, writeJSON(json: updateContent, returnText: true), headers)
    client.put("https://${keycloakDomain}/admin/realms/${currentTenantId}", writeJSON(json: ssoUpdates, returnText: true), headers)

    echo "Updated Keycloak configuration for tenant: ${currentTenantId} with URL: ${currentTenantUrl}"
  } else {
    echo "Warning: No Keycloak client found for tenant: ${currentTenantId}"
  }
                                                }

/**
 * Builds the list of redirect URIs for Keycloak client configuration.
 *
 * @param tenantId Original tenant ID
 * @param kongDomain Kong gateway domain
 * @param currentTenantUrl Current tenant URL
 * @param currentTenantId Current tenant ID being configured
 * @return List of redirect URIs
 */
private List<String> _buildRedirectUrisList(String tenantId, String kongDomain, String currentTenantUrl, String currentTenantId) {
  List<String> redirectUrisList = []

  // Add consortia-specific redirect URIs
  switch (tenantId) {
    case 'consortium':
      redirectUrisList << "https://ecs-${kongDomain}/*"
      break
    case 'consortium2':
      redirectUrisList << "https://ecs2-${kongDomain}/*"
      break
    case 'cs00000int':
      redirectUrisList << "https://ecs-${kongDomain}/*"
      break
  }

  // Add standard redirect URIs
  redirectUrisList.addAll([
    "${currentTenantUrl}/*",
    'http://localhost:3000/*',
    'http://localhost:3001/*',
    "https://eureka-snapshot-${currentTenantId}.${Constants.CI_ROOT_DOMAIN}/*"
  ])

  return redirectUrisList
}

private List<String> _updatePackageJsonFile(TenantUi tenantUi) {
  final String packageJsonFile = 'package.json'
  List<String> uiModulesToAdd = []

  // Safely read the package.json file
  Map packageJson
  try {
    packageJson = readJSON(file: packageJsonFile)
  } catch (Exception e) {
    echo "Error reading ${packageJsonFile}: ${e.message}"
    throw e
  }

  tenantUi.getCustomUiModules().each { customUiModule ->
    String uiModuleNameTransformed = customUiModule.name.replace('folio_', '@folio/')
    uiModulesToAdd << uiModuleNameTransformed
    packageJson['dependencies'][uiModuleNameTransformed] = customUiModule.version
  }

  // Safely write the updated package.json file
  try {
    writeJSON(file: packageJsonFile, json: packageJson, pretty: 2)
  } catch (Exception e) {
    echo "Error writing to ${packageJsonFile}: ${e.message}"
    throw e
  }

  // Log the modules added to the package
  echo "${packageJsonFile} updated with UI modules: ${uiModulesToAdd}"

  return uiModulesToAdd
}

//TODO refactoring and reviewing required.
@NonCPS
static def make_tpl(String tpl, Map data) {
  def ui_tpl = ((new StreamingTemplateEngine().createTemplate(tpl)).make(data)).toString()
  return ui_tpl
}

/**
 * Builds tenant options JSON for consortia central tenants including all member tenants
 * Uses actual tenant names from folioDefault.groovy configuration
 */
private String buildTenantOptionsJson(OkapiTenant tenant, boolean singleConsortiaUI = false) {
  String tenantId = tenant.getTenantId()

  if (singleConsortiaUI) {
    return buildSingleTenantOption(tenantId, tenant.getTenantName(), singleConsortiaUI)
  }

  Map<String, OkapiTenant> tenantsMap = getTenantsForConsortia(tenantId)

  if (!tenantsMap) {
    return buildSingleTenantOption(tenantId, tenant.getTenantName(), singleConsortiaUI)
  }

  List<String> tenantEntries = tenantsMap.collect { id, tenantObj ->
    buildTenantEntry(id, tenantObj.getTenantName(), singleConsortiaUI)
  }

  return "{${tenantEntries.join(', ')}}"
}

private Map<String, OkapiTenant> getTenantsForConsortia(String tenantId) {
  switch (tenantId) {
    case 'consortium':
      return folioDefault.consortiaTenants()
    case 'consortium2':
      return folioDefault.consortiaTenantsExtra()
    case 'cs00000int':
      return folioDefault.tenants().findAll { key, value ->
        key == 'cs00000int' || key.startsWith('cs00000int_')
      }
    default:
      return null
  }
}

private String buildTenantEntry(String tenantId, String tenantName, boolean singleUx) {
  String clientId = "${tenantId}-application"

  if (singleUx || !tenantName) {
    return "${tenantId}: {name: \"${tenantId}\", clientId: \"${clientId}\"}"
  } else {
    return "${tenantId}: {name: \"${tenantId}\", displayName: \"${tenantName}\", clientId: \"${clientId}\"}"
  }
}

private String buildSingleTenantOption(String tenantId, String tenantName, boolean singleUx) {
  String entry = buildTenantEntry(tenantId, tenantName, singleUx)
  return "{${entry}}"
}
