#!groovy
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.text.StreamingTemplateEngine
import org.folio.Constants
import org.folio.models.Modules
import org.folio.utilities.model.Module

void call(Map params) {
  stage('Checkout') {
    sh(script: "git clone --branch ${params.branch} --single-branch ${Constants.FOLIO_GITHUB_URL}/platform-complete.git" as String)
  }

  stage('Prepare') {
    dir('platform-complete') {
      sh(script: "cp -R -f eureka-tpl/* .")
      println("Parameters for UI:\n${JsonOutput.prettyPrint(JsonOutput.toJson(params))}")
      writeFile file: 'stripes.config.js', text: make_tpl(readFile(file: 'stripes.config.js', encoding: "UTF-8") as String, params), encoding: 'UTF-8'
    }
  }

  stage('Build and Push') {
    dir('platform-complete') {
      Module ui_bundle = new Module(
        name: "ui-bundle",
        hash: common.getLastCommitHash('platform-complete', params.branch as String)
      )
      ui_bundle.tag = "${params.cluster}-${params.namespace}.${params.tenantId}.${common.getLastCommitHash('platform-complete', params.branch as String).take(7)}"
      ui_bundle.imageName = "${Constants.ECR_FOLIO_REPOSITORY}/${ui_bundle.getName()}:${ui_bundle.getTag()}"

      if (params.consortia) {
        def packageJson = readJSON file: 'package.json'
        String moduleId = new Modules().getModuleVersion('folio_consortia-settings')
        String moduleVersion = moduleId - 'folio_consortia-settings-'
        packageJson.dependencies.put('@folio/consortia-settings', moduleVersion)
        writeJSON file: 'package.json', json: packageJson, pretty: 2
        sh 'sed -i "/modules: {/a \\    \'@folio/consortia-settings\' : {}," stripes.config.js'
      }

      docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}", "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
        retry(2) {
          def image = docker.build(
            ui_bundle.getImageName(),
            "--build-arg OKAPI_URL=${params.kongUrl} " +
              "--build-arg TENANT_ID=${params.tenantId} " +
              "-f docker/Dockerfile  " +
              "."
          )
          image.push()
        }
      }
      common.removeImage(ui_bundle.getImageName())
    }
  }

}

@NonCPS
static
def make_tpl(String tpl, Map data) {
  def ui_tpl = ((new StreamingTemplateEngine().createTemplate(tpl)).make(data)).toString()
  return ui_tpl
}
