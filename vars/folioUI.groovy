import com.cloudbees.groovy.cps.NonCPS
import groovy.text.StreamingTemplateEngine
import org.folio.Constants
import org.folio.jenkins.PodTemplates
import org.folio.models.EurekaTenant
import org.folio.models.RancherNamespace
import org.folio.models.TenantUi
import org.folio.models.module.EurekaModule
import org.folio.rest_v2.eureka.Keycloak
import org.folio.utilities.RestClient

import java.util.regex.Pattern

// Public API methods
void buildAndDeploy(RancherNamespace namespace, EurekaTenant tenant, boolean enableEcsRequests = false) {
  build(tenant, enableEcsRequests)
  deploy(namespace, tenant)
}

void build(EurekaTenant tenant, boolean enableEcsRequests) {
  TenantUi tenantUi = tenant.getTenantUi()
  PodTemplates podTemplates = new PodTemplates(this)

  podTemplates.stripesAgent {
    cleanWs(notFailBuild: true)

    _checkout(tenantUi.getHash())

    _preBuildConfigure(tenant, enableEcsRequests)

    stage('[UI] Build and push bundle') {
      container('werf') {
        writeFile file: 'werf.yaml', text: libraryResource('werf/platform-lsp/werf.yaml')
        writeFile file: 'werf-giterminism.yaml', text: libraryResource('werf/platform-lsp/werf-giterminism.yaml')

        // Add YARN_CACHE_FOLDER to the Dockerfile
        sh "sed -i '/^FROM /a ENV YARN_CACHE_FOLDER=${WORKSPACE}/.cache/yarn' docker/Dockerfile"

        withAWS(credentials: Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID, region: Constants.AWS_REGION) {
          String login = ecrLogin()

          sh """
            set -eu +x
            ${login.replace('docker', 'werf cr')}
            set -x
            export OKAPI_URL=https://${tenantUi.getKongDomain()}
            export TENANT_ID=${tenant.getTenantId()}
            werf build ${tenantUi.IMAGE_NAME} --repo ${Constants.ECR_FOLIO_REPOSITORY}/werf-shadow \
              --final-repo ${Constants.ECR_FOLIO_REPOSITORY}/${tenantUi.IMAGE_NAME} \
              --add-custom-tag ${tenantUi.getTag()} --loose-giterminism
          """
        }
      }
    }
  }
}

void deploy(RancherNamespace namespace, EurekaTenant tenant) {
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

    _postDeployConfigure(tenant.getTenantUi())
  }
}

// Build workflow methods
void _checkout(String branchOrHash) {
  stage('[UI] Checkout') {
    checkout(scmGit(branches: [[name: branchOrHash]],
      extensions: [cleanBeforeCheckout(deleteUntrackedNestedRepositories: true)],
      userRemoteConfigs: [[credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID,
                           url          : "${Constants.FOLIO_GITHUB_URL}/platform-lsp.git"]]))
  }
}

private void _preBuildConfigure(EurekaTenant tenant, boolean enableEcsRequests) {
  stage('[UI] Pre-build configuration') {
    echo "Pre-build configuration for tenant: ${tenant.getTenantId()}"

    TenantUi tenantUi = tenant.getTenantUi()
    Map packageJson = _readPackageJsonFile()
    String stripesConfig = _readStripesConfigFile()

    if (tenantUi.getRemoveUIComponents()) {
      _removeUIComponents(packageJson, tenantUi.getRemoveUIComponents())
    }

    if (tenantUi.getAddUIComponents()) {
      _addUIComponents(packageJson, tenantUi.getAddUIComponents())
    }

    String renderedConfig = _renderConfig(stripesConfig, tenant, tenant.getTenantUi(), enableEcsRequests)

    _writePackageJsonFile(packageJson)
    _writeStripesConfigFile(renderedConfig)
  }
}

private void _renderConfig(String stripesConfig, EurekaTenant tenant, TenantUi tenantUi, boolean enableEcsRequests) {
  stage('Render Config') {
    _overrideKongDomain(tenantUi)
    String tenantOptionsJson = _buildTenantOptionsJson(tenant, tenantUi.getIsConsortia(), tenantUi.getIsConsortiaSingleUi())

    Map tplData = [
      kongUrl          : "https://${tenantUi.getKongDomain()}",
      tenantUrl        : "https://${tenantUi.getDomain()}",
      keycloakUrl      : "https://${tenantUi.getKeycloakDomain()}",
      hasAllPerms      : false,
      isSingleTenant   : !tenantUi.getIsConsortia(),
      tenantOptions    : tenantOptionsJson,
      enableEcsRequests: enableEcsRequests,
      aboutInstallDate : String.format("'%s'", new Date().format('MMMM dd, yyyy')),
      aboutInstallMsg  : String.format("'%s'", "Branch: ${tenantUi.getBranch()}. Commit: ${tenantUi.getHash()}")
    ]

    String renderedConfig = _makeTpl(stripesConfig, tplData)
    return renderedConfig
  }
}

// Deploy workflow methods
private void _postDeployConfigure(TenantUi tenantUi) {
  stage('[UI] Update keycloak redirects') {
    String tenantId = tenantUi.getTenantId()
    List<String> tenantsToRedirect = _tenantsToRedirect(tenantId)

    tenantsToRedirect.each { currentTenantId ->
      _updateKeycloakClientConfiguration(tenantUi, currentTenantId)
    }
  }
}

// UI Component management methods
private void _addUIComponents(Map packageJson, List<EurekaModule> addUIComponents) {
  stage('Add UI Components') {
    List stipesExtraList = []
    addUIComponents.each { component ->
      String componentName = _transformComponentName(component.getName())
      if (!packageJson['dependencies'].containsKey(componentName)) {
        packageJson['dependencies'][componentName] = component.getVersion()
        echo "Added UI component: ${componentName} with version ${component.getVersion()}"
      } else {
        echo "UI component already exists, skipping addition: ${componentName}"
      }

      stipesExtraList.add(componentName)
    }

    String stipesExtraHeader = '''/*
DO NOT EDIT THIS FILE
This file is auto-generated by the build process.
Any changes made here will be overwritten.
*/
'''
    String stipesExtraLines = stipesExtraList.collect { componentName -> "  '${componentName}': {}," }.join('\n')

    String stripesExtraConfig = stipesExtraHeader +
      "module.exports = {\n" + stipesExtraLines + (stipesExtraLines ? "\n" : '') + "};\n"

    _writeStripesConfigFile(stripesExtraConfig, 'stripes.extra.js')
  }
}

private void _removeUIComponents(Map packageJson, List<EurekaModule> removeUIComponents) {
  stage('Remove UI Components') {
    String stripesModulesConfig = _readStripesConfigFile('stripes.modules.js')

    removeUIComponents.each { component ->
      String componentName = _transformComponentName(component.getName())
      if (packageJson['dependencies'].containsKey(componentName)) {
        packageJson['dependencies'].remove(componentName)
        echo "Removed UI component: ${componentName}"
      } else {
        echo "UI component not found, skipping removal: ${componentName}"
      }

      String escapedComponentName = Pattern.quote(componentName)
      stripesModulesConfig = stripesModulesConfig.replaceAll(/(?m)^\s*'${escapedComponentName}'\s*:\s*\{\},?\s*\n?/, '')
    }

    _writeStripesConfigFile(stripesModulesConfig, 'stripes.modules.js')
  }
}

/**
 * TODO
 * Placeholder for future implementation of UI component version updates. For deploy from feature branch.
 * @param packageJson
 * @param updateUIComponents
 */
private void _updateUIComponents(Map packageJson, List<EurekaModule> updateUIComponents) {
  println('Method _updateUIComponents is not implemented yet.')
}

// File I/O operations
private Map _readPackageJsonFile() {
  final String packageJsonFile = 'package.json'
  Map packageJson
  try {
    packageJson = readJSON(file: packageJsonFile)
  } catch (Exception e) {
    echo "Error reading ${packageJsonFile}: ${e.message}"
    throw e
  }
  return packageJson
}

private void _writePackageJsonFile(Map packageJson) {
  final String packageJsonFile = 'package.json'
  try {
    writeJSON(file: packageJsonFile, json: packageJson, pretty: 2)
  } catch (Exception e) {
    echo "Error writing to ${packageJsonFile}: ${e.message}"
    throw e
  }
}

private String _readStripesConfigFile(String filePath = 'stripes.config.js') {
  String stripesConfigContent
  try {
    stripesConfigContent = readFile(file: filePath)
  } catch (Exception e) {
    echo "Error reading ${filePath}: ${e.message}"
    throw e
  }
  return stripesConfigContent
}

private void _writeStripesConfigFile(String content, String filePath = 'stripes.config.js') {
  try {
    writeFile(file: filePath, text: content)
  } catch (Exception e) {
    echo "Error writing to ${filePath}: ${e.message}"
    throw e
  }
}

// Keycloak configuration methods
private void _updateKeycloakClientConfiguration(TenantUi tenantUi, String currentTenantId) {
  String tenantId = tenantUi.getTenantId()
  RestClient client = new RestClient(this, true)
  Keycloak keycloak = new Keycloak(this, tenantUi.getKeycloakDomain(), true)

  String token = keycloak.getAuthMasterTenantToken()
  Map headers = ['Authorization': "Bearer ${token}"]

  String realmUrl = keycloak.generateUrl("/admin/realms/${currentTenantId}/clients?clientId=${currentTenantId}-application")
  Map realm = client.get(realmUrl, headers).body.first()

  if (realm) {
    String baseUIDomain = tenantUi.getDomain()
    String currentUIDomain = tenantUi.getIsConsortiaSingleUi() ?
      baseUIDomain.replace(tenantId, currentTenantId) :
      baseUIDomain

    List<String> redirectList = _buildRedirectList(currentTenantId, currentUIDomain, tenantUi.getKongDomain())

    String updateRealmUrl = keycloak.generateUrl("/admin/realms/${currentTenantId}/clients/${realm['id']}")
    headers['Content-Type'] = 'application/json'
    String currentUIUrl = "https://${currentUIDomain}"
    Map updateRealmBody = [
      rootUrl                     : currentUIUrl,
      baseUrl                     : currentUIUrl,
      adminUrl                    : currentUIUrl,
      redirectUris                : redirectList,
      webOrigins                  : ['/*'],
      authorizationServicesEnabled: true,
      serviceAccountsEnabled      : true,
      attributes                  : ['post.logout.redirect.uris': "/*##${currentUIUrl}/*", login_theme: 'custom-theme']
    ]
    client.put(updateRealmUrl, writeJSON(json: updateRealmBody, returnText: true), headers)

    String updateSSOUrl = keycloak.generateUrl("/admin/realms/${currentTenantId}")
    Map updateSSOBoby = [
      resetPasswordAllowed: true
    ]
    client.put(updateSSOUrl, writeJSON(json: updateSSOBoby, returnText: true), headers)

    echo "Updated Keycloak configuration for tenant: ${currentTenantId} with redirects: \n${writeJSON(json: redirectList, returnText: true, pretty: 2)}"
  } else {
    echo "Warning: No Keycloak client found for tenant: ${currentTenantId}"
  }
}

private static List<String> _buildRedirectList(String currentTenantId, String currentUIDomain, String kongDomain) {
  return [
    'http://localhost:3000/*',
    'http://localhost:3001/*',
    "https://${kongDomain}/*",
    "https://${currentUIDomain}/*",
    "https://eureka-snapshot-${currentTenantId}.${Constants.CI_ROOT_DOMAIN}/*"
  ]
}

// Tenant configuration methods
private String _buildTenantOptionsJson(EurekaTenant tenant, boolean isConsortia, boolean singleConsortiaUI = false) {
  String tenantId = tenant.getTenantId()

  if (singleConsortiaUI || !isConsortia) {
    return "{${_buildTenantEntry(tenantId, tenant.getTenantName(), singleConsortiaUI)}}"
  }

  Map<String, EurekaTenant> consortiaTenants = _getConsortiaTenants(tenantId)

  List<String> tenantEntries = consortiaTenants.collect { id, consortiaTenant ->
    _buildTenantEntry(consortiaTenant.getTenantId(), consortiaTenant.getTenantName(), singleConsortiaUI)
  }

  return "{${tenantEntries.join(', ')}}"
}

private static String _buildTenantEntry(String tenantId, String tenantName, boolean singleConsortiaUI) {
  String clientId = "${tenantId}-application"

  if (singleConsortiaUI || !tenantName) {
    return "${tenantId}: {name: \"${tenantId}\", clientId: \"${clientId}\"}"
  } else {
    return "${tenantId}: {name: \"${tenantId}\", displayName: \"${tenantName}\", clientId: \"${clientId}\"}"
  }
}

private Map<String, EurekaTenant> _getConsortiaTenants(String tenantId) {
  if (!tenantId?.trim()) {
    throw new IllegalArgumentException('Tenant ID cannot be null or empty')
  }

  switch (tenantId) {
    case 'consortium':
      return folioDefault.consortiaTenants()
    case 'consortium2':
      return folioDefault.consortiaTenantsExtra()
    case 'cs00000int':
      return _getEcsTenants()
    default:
      echo "Warning: No consortia configuration found for tenant ID: ${tenantId}"
      return [:]
  }
}

/**
 * Optimized method to get ECS (cs00000int) tenants without creating all tenants.
 * Creates only the needed tenants to improve performance.
 *
 * @return Map of ECS tenant configurations
 */
@SuppressWarnings('GrMethodMayBeStatic')
private Map<String, EurekaTenant> _getEcsTenants() {
  Map<String, Object> allTenants = folioDefault.tenants()
  Map<String, Object> ecsOnlyTenants = allTenants.findAll { key, value ->
    key == 'cs00000int' || key.startsWith('cs00000int_')
  }
  return ecsOnlyTenants
}

private static List<String> _tenantsToRedirect(String tenantId) {
  switch (tenantId) {
    case 'consortium':
      return [tenantId, 'university', 'college']
    case 'consortium2':
      return [tenantId, 'university2', 'college2']
    case 'cs00000int':
      return [tenantId] + (1..11).collect { i -> "cs00000int_${String.format('%03d', i)}" }
    default:
      return [tenantId]
  }
}

// Domain and configuration utility methods
private static void _overrideKongDomain(TenantUi tenantUi) {
  switch (tenantUi.getTenantId()) {
    case 'fs09000002':
      tenantUi.setKongDomain("fs02-${tenantUi.getKongDomain()}")
      break
    case 'fs09000003':
      tenantUi.setKongDomain("fs03-${tenantUi.getKongDomain()}")
      break
    case 'cs00000int':
    case 'consortium':
      tenantUi.setKongDomain("ecs-${tenantUi.getKongDomain()}")
      break
    case 'consortium2':
      tenantUi.setKongDomain("ecs2-${tenantUi.getKongDomain()}")
      break
  }
}

// General utility methods
private static String _transformComponentName(String componentName) {
  return componentName.replace('folio_', '@folio/')
}

@NonCPS
/**
 * Renders a template string with the provided data.
 *
 * @param tpl The template string to be rendered.
 * @param data A map containing the data to populate the template.
 * @return The rendered template string.
 */
private static def _makeTpl(String tpl, Map data) {
  def ui_tpl = ((new StreamingTemplateEngine().createTemplate(tpl)).make(data)).toString()
  return ui_tpl
}
