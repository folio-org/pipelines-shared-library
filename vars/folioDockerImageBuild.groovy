import org.folio.Constants
import org.folio.utilities.Logger

void call(Map params) {

  Logger logger = new Logger(this, "dockerImageBuilder")

  stage('checkout') {
    dir("${params.NAME}") {
      checkout([$class: 'GitSCM', branches: [[name: '*/master']], extensions: [], userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/" + params.NAME + ".git"]]])
    }
  }
  if (params.buildRequired) {
    dir("${params.NAME}") {
      withMaven(jdk: "openjdk-17-jenkins-slave-all", maven: Constants.MAVEN_TOOL_NAME, options: [artifactsPublisher(disabled: true)]) {
        sh(script: "mvn clean install -DskipTests", returnStdOut: true)
      }
    }
  }
  stage('build & push') {
    dir("${params.NAME}") {
      logger.info("Build started for ${params.NAME} image...")
      dir("${params.NAME}") {
        docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}", "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
          if (params.NAME == 'folio-kong') {
            def image = docker.build(
              "${params.NAME}",
              "--build-arg TARGETARCH=amd64 " +
                "-f ./Dockerfile  " +
                "."
            )
          } else {
            def image = docker.build(
              "${params.NAME}",
              "-f ./Dockerfile  " +
                "."
            )
          }
          image.push()
        }
      }
    }
  }
}
