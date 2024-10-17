import groovy.json.JsonException
import org.folio.Constants
import org.folio.client.reportportal.ReportPortalClient
import org.folio.testing.IRunExecutionSummary
import org.folio.testing.TestType
import org.folio.testing.cypress.results.CypressRunExecutionSummary

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
void setupCommonEnvironmentVariables(String tenantUrl, String okapiUrl, String tenantId, String adminUsername,
                                     String adminPassword) {
  // Validate input parameters
  if (!tenantUrl || !okapiUrl || !tenantId || !adminUsername || !adminPassword) {
    throw new IllegalArgumentException("All parameters must be provided and cannot be empty.")
  }

  stage('Set env variables') {
    // Set environment variables
    env.CYPRESS_BASE_URL = tenantUrl
    env.CYPRESS_OKAPI_HOST = okapiUrl
    env.CYPRESS_OKAPI_TENANT = tenantId
    env.CYPRESS_diku_login = adminUsername
    env.CYPRESS_diku_password = adminPassword
    env.AWS_DEFAULT_REGION = Constants.AWS_REGION

    echo "Environment variables set for Cypress testing."
  }
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

/**
 * Executes Cypress tests within a Docker container.
 *
 * This function runs tests using the custom build name, and browser.
 * Optionally, results can be posted to TestRail if project and run IDs are provided.
 *
 * @param ciBuildId The name of the custom build.
 * @param browserName The name of the browser to run tests on.
 * @param execParameters Additional parameters for executing tests.
 * @param testrailProjectID The TestRail project ID (optional).
 * @param testrailRunID The TestRail run ID (optional).
 * @param workerId An optional identifier for the worker.
 */
void executeTests(String ciBuildId, String browserName, String execParameters,
                  String testrailProjectID = '', String testrailRunID = '', String workerId = '') {
  // Validate input parameters
  if (!ciBuildId || !browserName) {
    throw new IllegalArgumentException("ciBuildId, and browserName must be provided and cannot be empty.")
  }

  stage('Run tests') {
    String runId = generateRunId(workerId)
    String execString = createExecString(runId, browserName, ciBuildId, execParameters)

    runInDocker("worker-${runId}", {
      if (testrailProjectID?.trim() && testrailRunID?.trim()) {
        runTestsWithTestRail(testrailProjectID, testrailRunID, execString)
      } else {
        runTests(execString)
      }
    })
  }
}

/**
 * Generates a CI build id based on the provided name and current build ID.
 *
 * @param ciBuildId The custom build name provided by the user.
 * @return A formatted CI build id string.
 */
String getCiBuildId(String ciBuildId) {
  return ciBuildId?.trim() ? "#${ciBuildId.replaceAll(/[^A-Za-z0-9\s.]/, "").replace(' ', '_')}.${env.BUILD_ID}" : "#${env.BUILD_ID}"
}


/**
 * Generates a unique run ID based on the worker ID.
 *
 * @param workerId The optional identifier for the worker.
 * @return A string representing the run ID.
 */
String generateRunId(String workerId) {
  String runId = workerId?.trim() ? "${env.BUILD_ID}${workerId}" : env.BUILD_ID
  return runId.length() > 2 ? runId : "0${runId}"
}

/**
 * Creates the execution command string for Cypress tests.
 *
 * @param runId The run ID to be used in the display variable.
 * @param browserName The name of the browser to run tests on.
 * @param ciBuildId The name of the custom build.
 * @param execParameters Additional parameters for executing tests.
 * @return The command string for executing tests.
 */
@SuppressWarnings('GrMethodMayBeStatic')
String createExecString(String runId, String browserName, String ciBuildId, String execParameters) {
  return """
    export HOME=\$(pwd)
    export CYPRESS_CACHE_FOLDER=\$(pwd)/cache
    export DISPLAY=:${runId[-2..-1]}
    mkdir -p /tmp/.X11-unix
    Xvfb \$DISPLAY -screen 0 1920x1080x24 &
    env
    npx cypress-cloud run --parallel --record --browser ${browserName} --ci-build-id ${ciBuildId} ${execParameters}
    pkill Xvfb
  """
}


/**
 * Runs the tests using the provided command string.
 *
 * @param execString The command string to execute tests.
 */
void runTests(String execString) {
  try {
    sh execString
  } catch (Exception e) {
    echo "Error executing tests: ${e.message}"
    throw e
  }
}

/**
 * Posts test results to TestRail if project and run IDs are provided.
 *
 * @param testrailProjectID The TestRail project ID.
 * @param testrailRunID The TestRail run ID.
 * @param execString The command string for executing tests.
 */
void runTestsWithTestRail(String testrailProjectID, String testrailRunID, String execString) {
  execString = """
        export TESTRAIL_HOST=${Constants.CYPRESS_TESTRAIL_HOST}
        export TESTRAIL_PROJECTID=${testrailProjectID}
        export TESTRAIL_RUN_ID=${testrailRunID}
        export CYPRESS_allureReuseAfterSpec=true
    """ + execString

  echo "Test results will be posted to TestRail.\nProjectID: ${testrailProjectID},\nRunID: ${testrailRunID}"

  withCredentials([usernamePassword(credentialsId: 'testrail-ut56', passwordVariable: 'TESTRAIL_PASSWORD', usernameVariable: 'TESTRAIL_USERNAME')]) {
    runTests(execString)
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

/**
 * Sets up the Report Portal client and initializes the launch.
 *
 * @param reportPortalClient The Report Portal client.
 * @param execParameters The parameters for execution.
 * @return A string containing the combined execution parameters.
 */
String setupReportPortal(ReportPortalClient reportPortalClient) {
  stage('[ReportPortal] Config bind & launch') {
    try {
      String rpLaunchID = reportPortalClient.launch()
      echo "Report Portal Launch ID: ${rpLaunchID}"

      String portalExecParams = reportPortalClient.getExecParams()
      echo "Report portal execution parameters: ${portalExecParams}"

      return portalExecParams
    } catch (Exception e) {
      echo "Error during Report Portal setup: ${e.message}"
    }
  }
}

/**
 * Finalizes the Report Portal session.
 *
 * @param reportPortalClient The Report Portal client.
 */
void finalizeReportPortal(ReportPortalClient reportPortalClient) {
  stage("[ReportPortal] Finish run") {
    try {
      def response = reportPortalClient.launchFinish()
      echo "${response}"
    } catch (Exception e) {
      echo "Couldn't stop run in ReportPortal\nError: ${e.getMessage()}"
    }
  }
}

void unpackAllureReport(List resultPaths) {
  stage('[Allure] Unpack report') {
    for (path in resultPaths) {
      unstash name: path
      unzip zipFile: "${path}.zip", dir: path
    }
  }
}

/**
 * Generates and Publishes the Allure report from the test results.
 *
 * @param resultPaths The paths where test results are stored.
 */
void generateAndPublishAllureReport(List resultPaths) {
  stage('[Allure] Generate report') {
    script {
//      for (path in resultPaths) {
//        unstash name: path
//        unzip zipFile: "${path}.zip", dir: path
//      }
      def allureHome = tool type: 'allure', name: Constants.CYPRESS_ALLURE_VERSION
      sh "${allureHome}/bin/allure generate --clean ${resultPaths.collect { path -> "${path}/allure-results" }.join(" ")}"
    }
  }

  stage('[Allure] Publish report') {
    script {
      allure([includeProperties: false,
              jdk              : '',
              commandline      : Constants.CYPRESS_ALLURE_VERSION,
              properties       : [],
              reportBuildPolicy: 'ALWAYS',
              results          : resultPaths.collect { path -> [path: "${path}/allure-results"] }])
    }
  }
}

/**
 * Analyzes the results of the test run.
 *
 * @param testRunExecutionSummary The summary of the test run execution.
 * @return The updated test run execution summary.
 */
IRunExecutionSummary analyzeResults() {
  IRunExecutionSummary testRunExecutionSummary
  stage('[Report] Analyze results') {
    def jsonSuites = readJSON(file: "${env.WORKSPACE}/allure-report/data/suites.json")
    def jsonDefects = readJSON(file: "${env.WORKSPACE}/allure-report/data/categories.json")

    testRunExecutionSummary = CypressRunExecutionSummary.addFromJSON(jsonSuites)
    testRunExecutionSummary.addDefectsFromJSON(jsonDefects)
  }
  return testRunExecutionSummary
}

/**
 * Sends notifications to Slack if required.
 *
 * @param sendSlackNotification Indicates whether to send Slack notifications.
 * @param testRunExecutionSummary The summary of the test run execution.
 * @param ciBuildId The name of the build.
 * @param useReportPortal Indicates whether Report Portal is used.
 */
void sendNotifications(IRunExecutionSummary testRunExecutionSummary, String ciBuildId, boolean useReportPortal,
                       channel = '#rancher_tests_notifications') {
  stage('[Slack] Send notification') {
    slackSend(attachments: folioSlackNotificationUtils
      .renderBuildAndTestResultMessage(
        TestType.CYPRESS,
        testRunExecutionSummary,
        ciBuildId,
        useReportPortal,
        "${env.BUILD_URL}allure/"
      ),
      channel: channel)
  }
}

/**
 * Generates a random alphanumeric string of the specified length.
 *
 * @param length The desired length of the generated ID.
 * @return A random alphanumeric string.
 * @throws IllegalArgumentException if the length is less than or equal to zero.
 */
String generateRandomId(int length) {
  // Validate input parameter
  if (length <= 0) {
    throw new IllegalArgumentException("Length must be greater than zero.")
  }

  // Define the character pool
  def chars = ('A'..'Z') + ('0'..'9')
  Random random = new Random()

  // Generate the random ID
  return (1..length).collect { chars[random.nextInt(chars.size())] }.join()
}
