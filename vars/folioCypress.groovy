import org.folio.Constants

void setupCommonEnvironmentVariables(String tenantUrl, String okapiUrl, String tenantId, String adminUsername, String adminPassword) {
  // Validate input parameters
  if (!tenantUrl || !okapiUrl || !tenantId || !adminUsername || !adminPassword) {
    throw new IllegalArgumentException("All parameters must be provided and cannot be empty.")
  }

  // Set environment variables
  env.CYPRESS_BASE_URL = tenantUrl
  env.CYPRESS_OKAPI_HOST = okapiUrl
  env.CYPRESS_OKAPI_TENANT = tenantId
  env.CYPRESS_diku_login = adminUsername
  env.CYPRESS_diku_password = adminPassword
  env.AWS_DEFAULT_REGION = Constants.AWS_REGION

  println("Environment variables set for Cypress testing.")
}

void runInDocker(String containerNameSuffix, Closure<?> closure) {
  final String cypressImage = '732722833398.dkr.ecr.us-west-2.amazonaws.com/cypress/browsers:latest'
  String containerName = "cypress-${containerNameSuffix}"
  def containerObject
  try {
    // Authenticate with the Docker registry and run the container
    docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}", "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
      // Create and start the Docker container with the specified image
      containerObject = docker.image(cypressImage).inside("--init --name=${containerName} --entrypoint=") {
        // Set up AWS credentials for accessing necessary resources during closure execution
        withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                          credentialsId    : Constants.AWS_S3_SERVICE_ACCOUNT_ID,
                          accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                          secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
          // Execute the provided closure and return its result
          return closure()
        }
      }
    }
  } catch (e) {
    // Log the error message and update the build result based on the context
    println("Error occurred while running Docker container: ${e.message}")
    if (containerName.contains('cypress-compile')) {
      currentBuild.result = 'FAILED'
      error('Unable to compile tests')
    } else {
      currentBuild.result = 'UNSTABLE'
    }
  } finally {
    // Ensure that the Docker container is stopped to free up resources
    if (containerObject) {
      containerObject.stop()
    }
  }
}

String archiveTestResults(String id) {
  stage('Archive test results') {
    script {
      zip zipFile: "allure-results-${id}.zip", glob: "allure-results/*"
      archiveArtifacts allowEmptyArchive: true, artifacts: "allure-results-${id}.zip", fingerprint: true, defaultExcludes: false
      stash name: "allure-results-${id}", includes: "allure-results-${id}.zip"
      return "allure-results-${id}"
    }
  }
}
