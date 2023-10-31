#!groovy

import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-835') _

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

void deploy(params) {
  stage("Deploy UI bundle")
    folioHelm.withKubeConfig(params.CLUSTER()) {
      folioHelm.deployFolioModule(params.NAMESPACE, 'ui-bundle', params.UI_BUNDLE_TAG, false, params.TENANT_ID)
  }
}
