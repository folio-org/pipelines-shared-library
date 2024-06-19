package org.folio.eureka

import org.folio.Constants
import org.folio.utilities.Logger

class EurekaImage implements Serializable {
  public Object steps
  String moduleName
  Logger logger

  EurekaImage(Object context) {
    this.steps = context
    this.logger = new Logger(context, 'EurekaImage')
  }

  def prepare() {
    try {
      logger.info("Starting checkout for ${moduleName}...")
      steps.checkout([$class           : 'GitSCM',
                        branches         : [[name: '*/master']],
                        extensions       : [],
                        userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/${moduleName}.git"]]])
      logger.info("Checkout completed successfully for ${moduleName}")
    } catch (Error e) {
      logger.error("Checkout failed: ${e.getMessage()}")
    }
  }

  def compile() {
    try {
      logger.info("Starting Maven compile for ${moduleName}...")
      steps.withMaven(jdk: "openjdk-17-jenkins-slave-all",
        maven: Constants.MAVEN_TOOL_NAME) {
        steps.sh(script: "mvn clean install -DskipTests", returnStdOut: true)
      }
      logger.info("Maven compile completed successfully for ${moduleName}")
    } catch (Error e) {
      logger.error("Maven compile failed: ${e.getMessage()}")
    }
  }

  def build(String tag, String ARGS) {
    try {
      logger.info("Starting Docker build for ${moduleName} image...")
      steps.docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}",
        "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
        def image = steps.docker.build(moduleName, ARGS)
        image.push(tag)
        logger.info("Docker image pushed successfully for ${moduleName}")
      }
      steps.common.removeImage(moduleName)
      logger.info("Docker image removed for ${moduleName}")
    } catch (Error e) {
      logger.error("Docker build failed: ${e.getMessage()}")
    }
  }

  def imageTag() {
    def tag
    try {
      tag = steps.sh(script: "find target -name *.jar | cut -d \"/\" -f 2 | sed 's/....\$//'", returnStdOut: true)
    } catch (Error e) {
      logger.error(e.getMessage())
      tag = 'unknown'
    }
    return tag
  }

  def makeImage() {
    switch (moduleName) {
      case 'folio-kong':
        prepare()
        build(imageTag(), "--build-arg TARGETARCH=amd64 -f ./Dockerfile .")
        break
      case 'folio-keycloak':
        prepare()
        build(imageTag(), "-f ./Dockerfile .")
        break
      default:
        prepare()
        compile()
        build(imageTag(), "-f ./Dockerfile .")
        break
    }
  }
}
