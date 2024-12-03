#!groovy
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.text.StreamingTemplateEngine
import org.folio.Constants
import org.folio.models.OkapiTenant
import org.folio.models.RancherNamespace
import org.folio.models.TenantUi
import org.folio.models.module.FolioModule
import org.folio.utilities.RestClient

void build(String okapiUrl, OkapiTenant tenant, boolean isEureka = false, String kongDomain = ''
           , String keycloakDomain = '', boolean enableEcsRequests = false) {
  final String baseDir = "platform-complete-${tenant.getTenantId()}"
  TenantUi tenantUi = tenant.getTenantUi()

  stage('[UI] Checkout') {
    dir(baseDir) {
      cleanWs()
    }
    checkout([$class           : 'GitSCM',
              branches         : [[name: tenantUi.getHash()]],
              extensions       : [[$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true],
                                  [$class: 'RelativeTargetDirectory', relativeTargetDir: baseDir]],
              userRemoteConfigs: [[url: 'https://github.com/folio-org/platform-complete.git']]])
  }

  stage('[UI] Add folio extensions') {
    if (tenantUi.getCustomUiModules()) {
      dir(baseDir) {
        //TODO refactoring and reviewing required.
        if (isEureka) {
          String tenantId = tenant.getTenantId()
          okapiUrl = "https://${kongDomain}"
          sh "cp -R -f eureka-tpl/* ."
          Map binding = [
            kongUrl          : "https://${kongDomain}",
            tenantUrl        : "https://${tenantUi.getDomain()}",
            keycloakUrl      : "https://${keycloakDomain}",
            hasAllPerms      : false,
            isSingleTenant   : true,
            tenantOptions    : """{${tenantId}: {name: "${tenantId}", clientId: "${tenantId}-application"}}""",
            enableEcsRequests: enableEcsRequests
          ]

          if (tenantUi.getCustomUiModules()*.name.contains('folio_consortia-settings')) {
            binding.kongUrl = "https://ecs-${kongDomain}"
            binding.isSingleTenant = false
            okapiUrl = binding.kongUrl
          }

          writeFile(file: 'stripes.config.js'
            , text: make_tpl(readFile(file: 'stripes.config.js', encoding: "UTF-8") as String, binding)
            , encoding: 'UTF-8')

          List eurekaCustomUiModules = ["folio_authorization-policies",
                                        "folio_authorization-roles",
                                        "folio_plugin-select-application"]
          eurekaCustomUiModules.each { moduleName ->
            FolioModule uiModule = new FolioModule()
            uiModule.setName(moduleName)
            uiModule.setVersion(">=1.0.0")
            tenantUi.customUiModules.add(uiModule)
          }
        }

        List uiModulesToAdd = _updatePackageJsonFile(tenantUi)
        _updateStripesConfigJsFile(uiModulesToAdd)
      }
    }
  }
  stage('[UI] Build and Push') {
    dir(baseDir) {
      docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}"
        , "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
        def image = docker.build(
          tenantUi.getImageName(),
          "--build-arg OKAPI_URL=${okapiUrl} " +
            "--build-arg TENANT_ID=${tenant.getTenantId()} " +
            "-f docker/Dockerfile " +
            "."
        )
        image.push()
      }
    }
  }

  stage('[UI] Cleanup') {
    common.removeImage(tenantUi.getImageName())
  }

  //TODO Refactoring required
  stage('Update keycloak redirect') {
    if (isEureka) {
      String tenantId = tenant.getTenantId()
      RestClient client = new RestClient(this)
      Map headers = ['Content-Type': 'application/x-www-form-urlencoded']
      String tokenUrl = "https://${keycloakDomain}/realms/master/protocol/openid-connect/token"
      String tokenBody = "grant_type=password&username=admin&password=SecretPassword&client_id=admin-cli"

      Map response = client.post(tokenUrl, tokenBody, headers).body
      String token = response['access_token']
      headers.put("Authorization", "Bearer ${token}")

      String getRealmUrl = "https://${keycloakDomain}/admin/realms/${tenantId}/clients?clientId=${tenantId}-application"
      List realm = client.get(getRealmUrl, headers).body

      String updateRealmUrl = "https://${keycloakDomain}/admin/realms/${tenantId}/clients/${realm['id'].get(0)}"
      headers['Content-Type'] = 'application/json'
      String tenantUrl = "https://${tenantUi.getDomain()}"
      Map updateContent = [
        rootUrl                     : tenantUrl,
        baseUrl                     : tenantUrl,
        adminUrl                    : tenantUrl,
        redirectUris                : ["${tenantUrl}/*", "http://localhost:3000/*"], //Requested by AQA Team
        webOrigins                  : ["/*"],
        authorizationServicesEnabled: true,
        serviceAccountsEnabled      : true,
        attributes                  : ['post.logout.redirect.uris': "/*##${tenantUrl}/*", login_theme: 'custom-theme']
      ]
      client.put(updateRealmUrl, JsonOutput.toJson(updateContent), headers)
    }
  }
}

void deploy(RancherNamespace namespace, OkapiTenant tenant) {
  stage('[UI] Deploy bundle') {
    TenantUi tenantUi = tenant.getTenantUi()
    folioHelm.withKubeConfig(namespace.getClusterName()) {
      folioHelm.deployFolioModule(namespace, 'ui-bundle', tenantUi.getTag(), false, tenantUi.getTenantId())
    }
  }
}

void buildAndDeploy(RancherNamespace namespace, OkapiTenant tenant, boolean isEureka = false, String kongDomain = ''
                    , String keycloakDomain = '', boolean enableEcsRequests = false) {
  build("https://${namespace.getDomains()['okapi']}", tenant, isEureka, kongDomain, keycloakDomain, enableEcsRequests)
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
