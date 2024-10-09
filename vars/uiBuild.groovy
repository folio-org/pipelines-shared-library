#!groovy
import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.rest.model.OkapiTenant
import org.folio.utilities.model.Module
import org.folio.utilities.model.Project

void call(Map params, boolean releaseVersion = false) {
  OkapiTenant tenant = new OkapiTenant(id: params.tenant_id)
  Project project_config = new Project(
    clusterName: params.rancher_cluster_name,
    projectName: params.rancher_project_name,
    domains: [ui   : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, tenant.getId(), Constants.CI_ROOT_DOMAIN),
              okapi: common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'okapi', Constants.CI_ROOT_DOMAIN),
              edge : common.generateDomain(params.rancher_cluster_name, params.rancher_project_name, 'edge', Constants.CI_ROOT_DOMAIN)]
  )
  Module ui_bundle = new Module(
    name: "ui-bundle",
    hash: params.custom_hash?.trim() ? params.custom_hash : common.getLastCommitHash(params.folio_repository, params.folio_branch)
  )
  String okapi_url = params.custom_url?.trim() ? params.custom_url : "https://" + project_config.getDomains().okapi
  ui_bundle.tag = params.custom_tag?.trim() ? params.custom_tag : "${project_config.getClusterName()}-${project_config.getProjectName()}.${tenant.getId()}.${ui_bundle.getHash().take(7)}"
  ui_bundle.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${ui_bundle.getName()}:${ui_bundle.getTag()}"

  //TODO Temporary solution should be revised during refactoring
  stage('Checkout') {
    dir("platform-complete-${params.tenant_id}") {
      cleanWs()
    }
    checkout([$class           : 'GitSCM',
              branches         : [[name: ui_bundle.hash]],
              extensions       : [[$class: 'CloneOption', depth: 300, noTags: true, reference: '', shallow: true, timeout: 20],
                                  [$class: 'CleanBeforeCheckout'],
                                  [$class: 'RelativeTargetDirectory', relativeTargetDir: "platform-complete-${params.tenant_id}"]],
              userRemoteConfigs: [[url: 'https://github.com/folio-org/platform-complete.git']]])
    if (params.consortia) {
      dir("platform-complete-${params.tenant_id}") {
        def packageJson = readJSON file: 'package.json'
        String moduleId = getModuleVersion('folio_consortia-settings', releaseVersion)
        String moduleVersion = moduleId - 'folio_consortia-settings-'
        packageJson.dependencies.put('@folio/consortia-settings', moduleVersion)
        writeJSON file: 'package.json', json: packageJson, pretty: 2
        sh 'sed -i "/modules: {/a \\    \'@folio/consortia-settings\' : {}," stripes.config.js'
      }
    }
  }
  stage('Prepare ECR Creds') {
    container('jnlp') {
      withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                        credentialsId    : 'aws-ecr-rw-credentials',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {

        // Get the ECR login password
        def ecrPassword = sh(script: """
      aws ecr get-login-password --region us-west-2
    """, returnStdout: true).trim()

        // Create the base64-encoded "AWS:<password>" string
        def loginCommand = sh(script: """
      echo -n 'AWS:${ecrPassword}' | base64 -w 0
    """, returnStdout: true).trim()

        def ecrRegistry = "732722833398.dkr.ecr.us-west-2.amazonaws.com"

        // Create the Docker config.json with AWS ECR credentials
        println "loginCommand = " + loginCommand
        def dockerConfigJson = """
    {
      "auths": {
        "${ecrRegistry}": {
          "auth": "${loginCommand}"
        }
      }
    }
    """

        // Write the config.json file
        writeFile file: '/kaniko/.docker/config.json', text: dockerConfigJson
      }
    }
  }

  stage('Build and Push') {
    dir("platform-complete-${params.tenant_id}") {
      String imagename = ui_bundle.getImageName()
      container('kaniko') {
        sh """
        #!/busybox/sh
        /kaniko/executor --context docker/ --destination ${imagename} --build-arg OKAPI_URL=${okapi_url} --build-arg TENANT_ID=${tenant.getId()}
           """
      }
    }
  }
  stage('Cleanup') {
    common.removeImage(ui_bundle.getImageName())
  }
}

//TODO temporary solution should be revised
static String getModuleVersion(String moduleName, boolean releaseVersion = false) {
  String versionType = 'only'
  if (releaseVersion) {
    versionType = 'false'
  }
  URLConnection registry = new URL("https://folio-registry.dev.folio.org/_/proxy/modules?filter=${moduleName}&npmSnapshot=${versionType}&latest=1").openConnection()
  if (registry.getResponseCode().equals(200)) {
    return new JsonSlurperClassic().parseText(registry.getInputStream().getText())*.id.first()
  } else {
    throw new RuntimeException("Unable to get ${moduleName} version. Url: ${registry.getURL()}. Status code: ${registry.getResponseCode()}.")
  }
}
