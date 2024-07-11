package org.folio.eureka

import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.utilities.Logger

class EurekaImage implements Serializable {
  public Object steps
  String moduleName
  String branch = 'master'
  Logger logger

  EurekaImage(Object context) {
    this.steps = context
    this.logger = new Logger(context, 'EurekaImage')
  }

  def prepare() {
    try {
      logger.info("Starting checkout for ${moduleName}.")
      steps.checkout([$class           : 'GitSCM',
                      branches         : [[name: "*/${branch}"]],
                      extensions       : [],
                      userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/${moduleName}.git"]]])
      logger.info("Checkout completed successfully for ${moduleName}")
    } catch (Error e) {
      logger.warning("Checkout failed: ${e.getMessage()}")
    }
  }

  def compile() {
    try {
      logger.info("Starting Maven compile for ${moduleName}.")
      steps.withMaven(jdk: "openjdk-17-jenkins-slave-all",
        maven: Constants.MAVEN_TOOL_NAME) {
        steps.sh(script: "mvn clean install -DskipTests", returnStdout: true)
      }
      logger.info("Maven compile completed successfully for ${moduleName}")
    } catch (Error e) {
      logger.warning("Maven compile failed: ${e.getMessage()}")
    }
  }

  def build(String tag, String ARGS) {
    try {
      logger.info("Starting Docker build for ${moduleName} image.")
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
      logger.warning("Docker build failed: ${e.getMessage()}")
    }
  }

  def imageTag() {
    def tag = 'unknown'
    try {
      def pom = steps.readMavenPom file: 'pom.xml'
      if (pom) {
        tag = "${pom.getVersion()}.${steps.env.BUILD_NUMBER}"
      }
    } catch (Exception e) {
      tag = 'latest'
      logger.warning("Tag not found, falling back to tag=latest.\nError: " + e.getMessage())
    }
    return tag
  }

  void publishMD() {
    if (moduleName =~ /^mod-.*$/) {
      try {
        def pom = steps.readMavenPom file: 'pom.xml'
        if (pom) {
          def module = "${pom.getArtifactId()}-${pom.getVersion()}.${steps.env.BUILD_NUMBER}"
          steps.sh(script: "curl ${Constants.EUREKA_REGISTRY_URL}${module}.json --upload-file target/ModuleDescriptor.json")
          logger.info("ModuleDescriptor: ${Constants.EUREKA_REGISTRY_URL}${module}.json")
        }
      } catch (Exception e) {
        logger.warning("Failed to publish MD for ${moduleName}\nError: ${e.getMessage()}")
      }
    }
  }

  def updatePL() {
    try {
      def pom = steps.readMavenPom file: 'pom.xml'
      if (pom) {
        logger.info("Starting git clone for platform-complete.")
        steps.script {
          steps.withCredentials([steps.usernamePassword(credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID, passwordVariable: 'GIT_PASS', usernameVariable: 'GIT_USER')]) {
            steps.sh(script: "git clone -b snapshot --single-branch ${Constants.FOLIO_GITHUB_URL}/platform-complete.git")
            steps.dir('platform-complete') {
              def eureka_platform = steps.readFile file: "eureka-platform.json"
              def check = new JsonSlurperClassic().parseText("${eureka_platform}")
              def module = "${pom.getArtifactId()}-${pom.getVersion()}.${steps.env.BUILD_NUMBER}"
              if (module.toString() in check['id']) {
                logger.warning("${module} already exists!\nPlease update pom.xml to build a new image.")
              } else {
                check.each {
                  if (it['id'] =~ /${moduleName}/) {
                    it['id'] = "${module}" as String
                  }
                }
                steps.writeJSON(file: "eureka-platform.json", json: check, pretty: 0)
                steps.sh(script: "mv eureka-platform.json data.json && jq '.' data.json > eureka-platform.json")
                steps.sh(script: "rm -f data.json && git commit -am '[EPL] updated: ${module}'")
                steps.sh(script: "set +x && git push --set-upstream https://${steps.env.GIT_USER}:${steps.env.GIT_PASS}@github.com/folio-org/platform-complete.git snapshot")
                logger.info("Snapshot branch successfully updated\n${moduleName} version: ${module}")
              }
            }
          }
        }
      }
    } catch (Error e) {
      logger.warning("Update of PL in snapshot branch failed: ${e.getMessage()}")
    }
  }

  def makeImage() {
    switch (moduleName) {
      case 'folio-kong':
        prepare()
        build(imageTag() as String, "--build-arg TARGETARCH=amd64 -f ./Dockerfile .")
        break
      case 'folio-keycloak':
        prepare()
        build(imageTag() as String, "-f ./Dockerfile .")
        break
      default:
        prepare()
        compile()
        publishMD()
        updatePL()
        build(imageTag() as String, "-f ./Dockerfile .")
        break
    }
  }
}
