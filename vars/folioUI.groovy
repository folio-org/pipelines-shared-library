#!groovy

import org.folio.Constants

void build(params) {
  stage('Checkout') {
    dir('platform-complete') {
      cleanWs()
    }
    checkout([$class           : 'GitSCM',
              branches         : [[name: params.CUSTOM_HASH]],
              extensions       : [[$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true],
                                  [$class: 'RelativeTargetDirectory', relativeTargetDir: 'platform-complete']],
              userRemoteConfigs: [[url: 'https://github.com/folio-org/platform-complete.git']]])
    if (params.CONSORTIA) {
      dir('platform-complete') {
        def packageJson = readJSON file: 'package.json'
        String moduleId = folioStringScripts.getModuleId('folio_consortia-settings')
        String moduleVersion = moduleId - 'folio_consortia-settings-'
        packageJson.dependencies.put('@folio/consortia-settings', moduleVersion)
        writeJSON file: 'package.json', json: packageJson, pretty: 2
        sh 'sed -i "/modules: {/a \\    \'@folio/consortia-settings\' : {}," stripes.config.js'
      }
    }
  }

  stage('Build and Push') {
    dir('platform-complete') {
      docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}", "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
        def image = docker.build(
          params.IMAGE_NAME,
          "--build-arg OKAPI_URL=${params.OKAPI_URL} " +
            "--build-arg TENANT_ID=${params.TENANT_ID} " +
            "-f docker/Dockerfile  " +
            "."
        )
        image.push()
      }
    }
  }

  stage('Cleanup') {
    common.removeImage(params.IMAGE_NAME)
  }
}
