package org.folio.eureka

import org.folio.Constants
import org.folio.utilities.Logger
import org.folio.utilities.RestClient

class EurekaImage implements Serializable {
  public Object steps
  String moduleName
  String branch = 'master'
  Logger logger
  RestClient client

  EurekaImage(Object context) {
    this.steps = context
    this.logger = new Logger(context, 'EurekaImage')
  }

  def prepare() {
    try {
      logger.info("Starting checkout for ${moduleName}...")
      steps.checkout([$class           : 'GitSCM',
                      branches         : [[name: "*/${branch}"]],
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
        steps.sh(script: "mvn clean install -DskipTests", returnStdout: true)
      }
      logger.info("Maven compile completed successfully for ${moduleName}")
    } catch (Error e) {
      logger.error("Maven compile failed: ${e.getMessage()}")
    }
  }

  def build(String tag, String ARGS) {
    try {
      logger.info("Starting Docker build for ${moduleName} image...")
      steps.common.checkEcrRepoExistence(moduleName)
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
    def tag = 'unknown'
    try {
      def tmp = steps.sh(script: 'find target/ -name *.jar | cut -d "/" -f 2 | sed \'s/....$//\'', returnStdout: true).trim()
      if (tmp) {
        steps.script {
          def parts = tmp.split("-")
          def foundTag = parts.find { it.contains(".") }
          if (foundTag) {
            tag = foundTag.toString() + '-SNAPSHOT'
          } else {
            tag = 'unknown'
          }
        }
      } else {
        logger.warning('No JAR file found or empty result from script.')
      }
    } catch (Exception e) {
      logger.error(e.getMessage())
    }
    return tag
  }

  void publishMD() {
    if (moduleName ==~ 'mod-') {
      try {
        steps.script {
          def name = steps.sh(script: 'find target/ -name *.jar | cut -d "/" -f 2 | sed \'s/....$//\'', returnStdout: true).trim()
          def json = new File('target/ModuleDescriptor.json').text
          client.upload("${Constants.EUREKA_REGISTRY_URL}${name}.json", json as File)
        }
      } catch (Exception e) {
        logger.error("Failed to publish MD for ${moduleName}\nError: ${e.getMessage()}")
      }
    }
  }

  def makeImage() {
    switch (moduleName) {
      case 'folio-kong':
        prepare()
        compile()
        build(imageTag() as String, "--build-arg TARGETARCH=amd64 -f ./Dockerfile .")
        break
      default:
        prepare()
        compile()
        publishMD()
        build(imageTag() as String, "-f ./Dockerfile .")
        break
    }
  }
}
