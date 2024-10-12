import groovy.json.JsonException
import org.folio.Constants

/**
 * Clones the specified branch of the Cypress repository from Git.
 *
 * This function checks out the specified branch of the Cypress repository,
 * using the provided SSH credentials for authentication.
 *
 * @param branch The name of the branch to check out.
 * @throws IllegalArgumentException if the branch name is null or empty.
 */
void cloneCypressRepo(String branch) {
  // Validate input parameter
  if (!branch) {
    throw new IllegalArgumentException("Branch name must be provided and cannot be empty.")
  }

  stage('Checkout Cypress repo') {
    script {
      try {
        echo "Checking out branch: ${branch}"
        checkout([$class           : 'GitSCM',
                  branches         : [[name: "*/${branch}"]],
                  extensions       : [[$class: 'CloneOption', depth: 1, noTags: true, reference: '', shallow: true],
                                      [$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true]],
                  userRemoteConfigs: [[credentialsId: Constants.GITHUB_SSH_CREDENTIALS_ID,
                                       url          : Constants.CYPRESS_SSH_REPOSITORY_URL]]])
      } catch (Exception e) {
        // Log an error if the checkout fails
        echo "Failed to checkout branch '${branch}': ${e.message}"
        throw e
      }
    }
  }
}

/**
 * Sets up common environment variables for Cypress testing.
 *
 * This function initializes environment variables used by Cypress tests
 * based on the provided tenant and AWS configuration parameters.
 *
 * @param tenantUrl The base URL of the tenant being tested.
 * @param okapiUrl The URL of the Okapi service.
 * @param tenantId The ID of the tenant.
 * @param adminUsername The username for the admin account.
 * @param adminPassword The password for the admin account.
 *
 * @throws IllegalArgumentException if any required parameter is null or empty.
 */
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

/**
 * Compiles Cypress tests within a Docker container.
 *
 * This function runs the test compilation process using a specified batch ID for identifying the build context.
 *
 * @param batchID An optional identifier for the batch, defaults to an empty string.
 *
 */
void compileCypressTests(String batchID = '') {
  stage('Compile tests') {
    echo "Batch ID: ${batchID}"

    batchID = batchID ? "-${batchID}" : ''

    runInDocker("compile-${env.BUILD_ID}${batchID}", {
      sh """export HOME=\$(pwd)
            export CYPRESS_CACHE_FOLDER=\$(pwd)/cache

            node -v
            yarn -v

            yarn config set @folio:registry ${Constants.FOLIO_NPM_REPO_URL}
            env
            yarn install

            yarn add -D cypress-testrail-simple@${readPackageJsonDependencyVersion('./package.json', 'cypress-testrail-simple')}
            yarn global add cypress-cloud@${readPackageJsonDependencyVersion('./package.json', 'cypress-cloud')}
      """
      // Uncomment to add Report Portal agent for Cypress
      // sh "yarn add @reportportal/agent-js-cypress@latest"
    })
  }
}

void executeTests(String cypressImageVersion, String customBuildName, String browserName, String execParameters,
                  String testrailProjectID = '', String testrailRunID = '', String workerId = '') {
  stage('Run tests') {
    String runId = workerId?.trim() ? "${env.BUILD_ID}${workerId}" : env.BUILD_ID
    runId = runId.length() > 2 ? runId : "0${runId}"
    String execString = """
      export HOME=\$(pwd); export CYPRESS_CACHE_FOLDER=\$(pwd)/cache
      export DISPLAY=:${runId[-2..-1]}
      mkdir -p /tmp/.X11-unix
      Xvfb \$DISPLAY -screen 0 1920x1080x24 &
      env; npx cypress-cloud run --parallel --record --browser ${browserName} --ci-build-id ${customBuildName} ${execParameters}
      pkill Xvfb
    """

    runInDocker(cypressImageVersion, "worker-${runId}", {
      if (testrailProjectID?.trim() && testrailRunID?.trim()) {
        execString = """
        export TESTRAIL_HOST=${Constants.CYPRESS_TESTRAIL_HOST}
        export TESTRAIL_PROJECTID=${testrailProjectID}
        export TESTRAIL_RUN_ID=${testrailRunID}
        export CYPRESS_allureReuseAfterSpec=true
        """ + execString

        println "Test results will be posted to TestRail.\nProjectID: ${testrailProjectID},\nRunID: ${testrailRunID}"
        withCredentials([usernamePassword(credentialsId: 'testrail-ut56', passwordVariable: 'TESTRAIL_PASSWORD', usernameVariable: 'TESTRAIL_USERNAME')]) {
          sh execString
        }
      } else {
        sh execString
      }
    })
  }
}

/**
 * Executes a specified closure within a Docker container configured for Cypress testing.
 *
 * This function creates a Docker container using a specified image and runs the provided closure inside it.
 * The Docker container is initialized with specific entry points and AWS credentials to allow access
 * to required resources during the execution of the closure. The function also handles potential errors
 * and ensures that the Docker container is stopped after execution to free up resources.
 *
 * @param containerNameSuffix A suffix for the container name, which helps identify the context or
 *                            purpose of the container. It is appended to the base name "cypress-"
 *                            to create a unique container name.
 * @param closure A Closure that contains the code to be executed inside the Docker container. This
 *                closure should encapsulate the Cypress tests or any setup required for testing.
 *
 * @throws IllegalArgumentException if containerNameSuffix or closure is null.
 */
void runInDocker(String containerNameSuffix, Closure<?> closure) {
  // Validate input parameters
  if (!containerNameSuffix || !closure) {
    throw new IllegalArgumentException("Both containerNameSuffix and closure must be provided and cannot be empty.")
  }

  final String cypressImage = '732722833398.dkr.ecr.us-west-2.amazonaws.com/cypress/browsers:latest'
  String containerName = "cypress-${containerNameSuffix}"
  def containerObject

  try {
    echo "Starting Docker container: ${containerName} using image: ${cypressImage}"

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
          echo "Executing closure in Docker container..."
          // Execute the provided closure and return its result
          return closure()
        }
      }
    }

    echo "Successfully executed closure in Docker container: ${containerName}"

  } catch (Exception e) {
    // Log the error message and update the build result based on the context
    echo "Error occurred while running Docker container: ${e.message}"
    if (containerName.contains('cypress-compile')) {
      currentBuild.result = 'FAILED'
      error 'Unable to compile tests'
    } else {
      currentBuild.result = 'UNSTABLE'
    }
  } finally {
    // Ensure that the Docker container is stopped to free up resources
    if (containerObject) {
      containerObject.stop()
      echo "Docker container stopped: ${containerName}"
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
