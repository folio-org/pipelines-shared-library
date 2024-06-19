package org.folio.eureka

import org.folio.Constants
import org.folio.utilities.Logger

class EurekaImage implements Serializable {
  private Object context
  String moduleName
  Logger logger

  EurekaImage(context) {
    this.context = context
    this.logger = new Logger(context, 'EurekaImage')
  }

  def prepare() {
    try {
      logger.info("Starting checkout for ${moduleName}...")
      context.checkout([$class           : 'GitSCM',
                        branches         : [[name: '*/master']],
                        extensions       : [],
                        userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/${moduleName}.git"]]])
      logger.info("Checkout completed successfully for ${moduleName}")
    } catch (Exception e) {
      logger.warning("Checkout failed: ${e.getMessage()}")
    }
  }

  def compile() {
    try {
      logger.info("Starting Maven compile for ${moduleName}...")
      context.withMaven(jdk: "openjdk-17-jenkins-slave-all",
        maven: Constants.MAVEN_TOOL_NAME) {
        context.sh(script: "mvn clean install -DskipTests", returnStdOut: true)
      }
      logger.info("Maven compile completed successfully for ${moduleName}")
    } catch (Exception e) {
      logger.warning("Maven compile failed: ${e.getMessage()}")
    }
  }

  def build(String tag, String ARGS) {
    try {
      logger.info("Starting Docker build for ${moduleName} image...")
      context.docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}",
        "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
        def image = context.docker.build(moduleName, ARGS)
        image.push(tag)
        logger.info("Docker image pushed successfully for ${moduleName}")
      }
      context.common.removeImage(moduleName)
      logger.info("Docker image removed for ${moduleName}")
    } catch (Exception e) {
      logger.warning("Docker build failed: ${e.getMessage()}")
    }
  }

  def imageTag() {
    context.sh(script: "ls -la", returnStdOut: true)
    def fileNames = new FileNameFinder().getFileNames(".", "target/${moduleName}*.jar")
    if (fileNames.size() > 0) {
      def jarFileName = fileNames[0]
      def jarName = jarFileName.split("/").find { it.endsWith(".jar") }
      if (jarName != null) {
        def tag = jarName.replace(".jar", "")
        println "Module name: $tag"
        return tag
      } else {
        println "No .jar file found in the path target"
      }
    } else {
      println "No files found matching the pattern *.jar"
    }
  }

  def makeImage() {
    switch (moduleName) {
      case 'folio-kong':
        prepare()
        build(imageTag().toString(), "--build-arg TARGETARCH=amd64 -f ./Dockerfile .")
        break
      case 'folio-keycloak':
        prepare()
        build(imageTag().toString(), "-f ./Dockerfile .")
        break
      default:
        prepare()
        compile()
        build(imageTag().toString(), "-f ./Dockerfile .")
        break
    }
  }
}
