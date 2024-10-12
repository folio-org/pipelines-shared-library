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

  echo "Environment variables set for Cypress testing."
}

void runInDocker(String containerNameSuffix, Closure<?> closure) {
  final String cypressImage = '732722833398.dkr.ecr.us-west-2.amazonaws.com/cypress/browsers:latest'
  String containerName = "cypress-${containerNameSuffix}"
  def containerObject
  try {
    // Authenticate with the Docker registry and run the container
    docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}",
      "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
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
    echo "Error occurred while running Docker container: ${e.message}"
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

String archiveTestResults(String workerId) {
  stage('Archive test results') {
    script {
      try {
        // Zipping allure results
        zip zipFile: "allure-results-${workerId}.zip", glob: "allure-results/*"

        // Archiving the zipped file
        archiveArtifacts artifacts: "allure-results-${workerId}.zip", allowEmptyArchive: true, fingerprint: true,
          defaultExcludes: false

        // Stashing the artifacts for later use in other stages
        stash name: "allure-results-${workerId}", includes: "allure-results-${workerId}.zip"

        return "allure-results-${workerId}"
      } catch (Exception e) {
        // Log an error if something goes wrong
        echo "Failed to archive and stash test results: ${e.message}"
        throw e
      }
    }
  }
}

String readPackageJsonDependencyVersion(String filePath, String dependencyName) {
  def packageJson = readJSON file: filePath
  return packageJson['dependencies'][dependencyName] ?: packageJson['devDependencies'][dependencyName]
}
