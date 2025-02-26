#!groovy

import org.folio.Constants
import org.folio.jenkins.JenkinsAgentLabel
import org.folio.jenkins.PodTemplates
import org.folio.models.OkapiTenant
import org.folio.models.RancherNamespace
import org.folio.models.TenantUi

void build(String okapiUrl, OkapiTenant tenant) {
  TenantUi tenantUi = tenant.getTenantUi()
  PodTemplates podTemplates = new PodTemplates(this, true)

  podTemplates.stripesTemplate {
    node(JenkinsAgentLabel.STRIPES_AGENT.getLabel()) {
      stage('[UI] Checkout') {
        cleanWs()

        checkout([$class           : 'GitSCM',
                  branches         : [[name: tenantUi.getHash()]],
                  extensions       : [[$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true]],
                  userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/platform-complete.git"]]])
      }

      stage('[UI] Add folio extensions') {
        if (tenantUi.getCustomUiModules()) {
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
    }
  }
}

void deploy(RancherNamespace namespace, OkapiTenant tenant) {
  PodTemplates podTemplates = new PodTemplates(this)

  podTemplates.defaultTemplate {
    node(JenkinsAgentLabel.DEFAULT_AGENT.getLabel()) {
      stage('[UI] Deploy bundle') {
        TenantUi tenantUi = tenant.getTenantUi()
        folioHelm.withKubeConfig(namespace.getClusterName()) {
          folioHelm.deployFolioModule(namespace, 'ui-bundle', tenantUi.getTag(), false, tenantUi.getTenantId())
        }
      }
    }
  }
}

void buildAndDeploy(RancherNamespace namespace, OkapiTenant tenant) {
  build("https://${namespace.getDomains()['okapi']}", tenant)
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
