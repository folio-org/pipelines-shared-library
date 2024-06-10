package org.folio.eurekaImage

import org.folio.Constants
import org.folio.utilities.Logger

class eurekaImage {
  Logger logger = new Logger(this, 'eurekaImage')

  String moduleName

  def compile() {
    withMaven(jdk: "openjdk-17-jenkins-slave-all", maven: Constants.MAVEN_TOOL_NAME, options: [artifactsPublisher(disabled: true)]) {
      sh(script: "mvn clean install -DskipTests", returnStdOut: true)
    }
  }

  def build(String ARGS) {
    logger.info("Build started for ${moduleName} image...")
    docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}", "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
      def image = docker.build(
        moduleName,
        ARGS
      )
      image.push()
    }
  }

  def makeImage() {
    switch (moduleName) {
      case 'folio-kong':
        build("--build-arg TARGETARCH=amd64 -f ./Dockerfile .")
        break
      case 'folio-keycloak':
        build("-f ./Dockerfile .")
        break
      default:
        compile()
        build("-f ./Dockerfile .")
        break
    }
  }
}
