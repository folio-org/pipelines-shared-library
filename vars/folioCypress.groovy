import groovy.json.JsonException
import org.folio.Constants

void cloneCypressRepo(String branch) {
  stage('Checkout Cypress repo') {
    script {
      checkout([$class           : 'GitSCM',
                branches         : [[name: "*/${branch}"]],
                extensions       : [[$class: 'CloneOption', depth: 1, noTags: true, reference: '', shallow: true],
                                    [$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true]],
                userRemoteConfigs: [[credentialsId: Constants.GITHUB_SSH_CREDENTIALS_ID,
                                     url          : Constants.CYPRESS_SSH_REPOSITORY_URL]]])
    }
  }
}

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

/**
 * Archives and stashes cypress test results from the specified worker.
 *
 * This function zips the allure results, archives the zipped file,
 * and stashes it for use in later stages of the Jenkins pipeline.
 *
 * @param workerId The ID of the worker whose test results are being archived.
 * @return The name of the archived and stashed file.
 * @throws IllegalArgumentException if workerId is null or empty.
 */
String archiveTestResults(String workerId) {
  // Validate input parameter
  if (!workerId) {
    throw new IllegalArgumentException("workerId must be provided and cannot be empty.")
  }

  stage('Archive test results') {
    script {
      try {
        // Define the zip file name
        String zipFileName = "allure-results-${workerId}.zip"

        // Zipping allure results
        zip zipFile: zipFileName, glob: "allure-results/*"

        // Archiving the zipped file
        archiveArtifacts artifacts: zipFileName, allowEmptyArchive: true, fingerprint: true,
          defaultExcludes: false

        // Stashing the artifacts for later use in other stages
        stash name: "allure-results-${workerId}", includes: zipFileName

        echo "Successfully archived and stashed test results for worker: ${workerId}"

        return zipFileName
      } catch (Exception e) {
        // Log an error if something goes wrong
        echo "Failed to archive and stash test results: ${e.message}"
        throw e
      }
    }
  }
}

/**
 * TODO Move to common lib
 * Reads the version of a specified dependency from a package.json file.
 *
 * @param filePath The path to the package.json file.
 * @param dependencyName The name of the dependency whose version is to be read.
 * @return The version of the specified dependency, or null if not found.
 * @throws FileNotFoundException if the package.json file does not exist.
 * @throws IOException if an error occurs while reading the file.
 * @throws JsonException if the file is not a valid JSON.
 */
String readPackageJsonDependencyVersion(String filePath, String dependencyName) {
  // Validate input parameters
  if (!filePath || !dependencyName) {
    throw new IllegalArgumentException("Both filePath and dependencyName must be provided.")
  }

  // Read the package.json file
  def packageJson
  try {
    packageJson = readJSON(file: filePath)
  } catch (FileNotFoundException e) {
    throw new FileNotFoundException("The specified file does not exist: ${filePath}")
  } catch (IOException e) {
    throw new IOException("Error reading the file: ${filePath}")
  } catch (JsonException e) {
    throw new JsonException("The file is not a valid JSON: ${filePath}")
  }

  // Return the version of the specified dependency
  def version = packageJson['dependencies'][dependencyName] ?: packageJson['devDependencies'][dependencyName]

  if (!version) {
    echo "Dependency '${dependencyName}' not found in package.json."
  }
  return version
}
