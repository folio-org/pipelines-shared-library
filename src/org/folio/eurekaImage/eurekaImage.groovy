package org.folio.eurekaImage

import org.folio.Constants
import org.folio.utilities.Logger

class eurekaImage {

  private Object context

  eurekaImage(context) {
    this.context = context
  }

  Logger logger = new Logger(this, 'eurekaImage')

  String moduleName

  def prepare() {
    try {
      checkout([$class: 'GitSCM', branches: [[name: '*/master']], extensions: [], userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/${moduleName}.git"]]])
    } catch (Exception e) {
      logger.warning(e.getMessage())
    }
  }

  def compile() {
    try {
      withMaven(jdk: "openjdk-17-jenkins-slave-all", maven: Constants.MAVEN_TOOL_NAME, options: [artifactsPublisher(disabled: true)]) {
        sh(script: "mvn clean install -DskipTests", returnStdOut: true)
      }
    } catch (Exception e) {
      logger.warning(e.getMessage())
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
    common.removeImage("${moduleName}")
  }

  def makeImage() {
    switch ("${moduleName}") {
      case 'folio-kong':
        prepare()
        build("--build-arg TARGETARCH=amd64 -f ./Dockerfile .")
        break
      case 'folio-keycloak':
        prepare()
        build("-f ./Dockerfile .")
        break
      default:
        prepare()
        compile()
        build("-f ./Dockerfile .")
        break
    }
  }
}
