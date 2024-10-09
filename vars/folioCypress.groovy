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
 * @param branch The name of the branch to check out. Must not be null or empty.
 * @throws IllegalArgumentException if the branch name is null or empty.
 */
void cloneCypressRepo(String branch) {
  // Validate input parameter
  if (!branch) {
    throw new IllegalArgumentException("Branch name must be provided and cannot be empty.")
  }

  stage('[Git] Checkout Cypress repo') {
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
 * @param tenantUrl The base URL of the tenant being tested. Must not be null or empty.
 * @param okapiUrl The URL of the Okapi service. Must not be null or empty.
 * @param tenantId The ID of the tenant. Must not be null or empty.
 * @param adminUsername The username for the admin account. Must not be null or empty.
 * @param adminPassword The password for the admin account. Must not be null or empty.
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
 * @param batchId An optional identifier for the batch, defaults to an empty string.
 */
void compileCypressTests(String batchId = '') {
  stage('[Yarn] Compile Cypress tests') {
    echo "Batch ID: ${batchId}"

    batchId = batchId ? "-${batchId}" : ''

    runInDocker("compile-${env.BUILD_ID}${batchId}") {
      sh """export HOME=\$(pwd)
        export CYPRESS_CACHE_FOLDER=\$(pwd)/cache

        node -v
        yarn -v

        yarn config set @folio:registry ${Constants.FOLIO_NPM_REPO_URL}
        yarn install
        yarn add -D cypress-testrail-simple@${readPackageJsonDependencyVersion('./package.json', 'cypress-testrail-simple')}
        yarn global add cypress-cloud@${readPackageJsonDependencyVersion('./package.json', 'cypress-cloud')}
      """
      // Uncomment to add Report Portal agent for Cypress
      // sh "yarn add @reportportal/agent-js-cypress@latest"
    }
  }
}

/**
 * Executes Cypress tests within a Docker container.
 *
 * This function runs tests using the custom build name, and browser.
 * Optionally, results can be posted to TestRail if project and run IDs are provided.
 *
 * @param ciBuildId The name of the custom build. Must not be null or empty.
 * @param browserName The name of the browser to run tests on. Must not be null or empty.
 * @param execParameters Additional parameters for executing tests. Must not be null or empty.
 * @param workerId An optional identifier for the worker. Must not be null or empty.
 * @param testrailProjectID The TestRail project ID (optional).
 * @param testrailRunID The TestRail run ID (optional).
 * @throws IllegalArgumentException if ciBuildId, browserName, execParameters, or workerId is null or empty.
 */
void executeTests(String ciBuildId, String browserName, String execParameters, String workerId,
                  String testrailProjectID = '', String testrailRunID = '') {
  // Validate input parameters
  if (!ciBuildId || !browserName || !execParameters || !workerId) {
    throw new IllegalArgumentException("ciBuildId, browserName, execParameters, and workerId must be provided and cannot be empty.")
  }

  stage('[Cypress] Run tests') {
    String execString = createExecString(browserName, ciBuildId, execParameters)

    runInDocker("worker-${workerId}") {
      if (testrailProjectID?.trim() && testrailRunID?.trim()) {
        runTestsWithTestRail(testrailProjectID, testrailRunID, execString)
      } else {
        runTests(execString)
      }
    }
  }
}

@SuppressWarnings('GrMethodMayBeStatic')
/**
 * Creates the execution command string for Cypress tests.
 *
 * @param browserName The name of the browser to run tests on. Must not be null or empty.
 * @param ciBuildId The name of the custom build. Must not be null or empty.
 * @param execParameters Additional parameters for executing tests. Must not be null.
 * @return The command string for executing tests.
 */
String createExecString(String browserName, String ciBuildId, String execParameters) {
  // Generate a random screen ID for Xvfb
  String screenId = (new Random().nextInt(90) + 10).toString()
  return """
    export HOME=\$(pwd)
    export CYPRESS_CACHE_FOLDER=\$(pwd)/cache
    export DISPLAY=:${screenId}
    mkdir -p /tmp/.X11-unix
    Xvfb \$DISPLAY -screen 0 1920x1080x24 &
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
 * @param testrailProjectID The TestRail project ID. Must not be null or empty.
 * @param testrailRunID The TestRail run ID. Must not be null or empty.
 * @param execString The command string for executing tests. Must not be null or empty.
 */
void runTestsWithTestRail(String testrailProjectID, String testrailRunID, String execString) {
  execString += """
        export TESTRAIL_HOST=${Constants.CYPRESS_TESTRAIL_HOST}
        export TESTRAIL_PROJECTID=${testrailProjectID}
        export TESTRAIL_RUN_ID=${testrailRunID}
        export CYPRESS_allureReuseAfterSpec=true
    """

  echo "Test results will be posted to TestRail.\nProjectID: ${testrailProjectID},\nRunID: ${testrailRunID}"

  withCredentials([usernamePassword(credentialsId: 'testrail-ut56', passwordVariable: 'TESTRAIL_PASSWORD',
    usernameVariable: 'TESTRAIL_USERNAME')]) {
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
 *                            to create a unique container name. Must not be null or empty.
 * @param closure A Closure that contains the code to be executed inside the Docker container.
 *                This closure should encapsulate the Cypress tests or any setup required for testing.
 *                Must not be null.
 * @throws IllegalArgumentException if containerNameSuffix or closure is null or empty.
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
          closure()
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
 * Archives and stashes Cypress test results from the specified worker.
 *
 * This function zips the allure results, archives the zipped file,
 * and stashes it for use in later stages of the Jenkins pipeline.
 *
 * @param workerId The ID of the worker whose test results are being archived. Must not be null or empty.
 * @return The name of the archived and stashed file.
 * @throws IllegalArgumentException if workerId is null or empty.
 */
String archiveTestResults(String workerId) {
  // Validate input parameter
  if (!workerId) {
    throw new IllegalArgumentException("workerId must be provided and cannot be empty.")
  }

  stage('[Stash] Archive test results') {
    try {
      String stashName = "allure-results-${workerId}"
      // Define the zip file name
      String zipFileName = "${stashName}.zip"

      // Zipping allure results
      zip zipFile: zipFileName, glob: "allure-results/*"

      // Archiving the zipped file
      archiveArtifacts artifacts: zipFileName, allowEmptyArchive: true, fingerprint: true, defaultExcludes: false

      // Stashing the artifacts for later use in other stages
      stash name: stashName, includes: zipFileName

      echo "Successfully archived and stashed test results for worker: ${workerId}"

      return stashName
    } catch (Exception e) {
      // Log an error if something goes wrong
      echo "Failed to archive and stash test results: ${e.message}"
      throw e
    }
  }
}

/**
 * Reads the version of a specified dependency from a package.json file.
 *
 * This function retrieves the version of a specified dependency from the package.json file.
 * If the dependency is not found, it returns null.
 *
 * @param filePath The path to the package.json file. Must not be null or empty.
 * @param dependencyName The name of the dependency whose version is to be read. Must not be null or empty.
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
 * This function configures the Report Portal client and starts a new test launch.
 *
 * @param reportPortalClient The Report Portal client. Must not be null.
 * @param execParameters The parameters for execution. Must not be null or empty.
 * @return A string containing the combined execution parameters.
 * @throws IllegalArgumentException if reportPortalClient is null or execParameters is empty.
 */
String setupReportPortal(ReportPortalClient reportPortalClient) {
  // Validate input parameter
  if (!reportPortalClient) {
    throw new IllegalArgumentException("ReportPortalClient must be provided and cannot be null.")
  }

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
 * This function marks the end of the test execution in Report Portal.
 *
 * @param reportPortalClient The Report Portal client. Must not be null.
 * @throws IllegalArgumentException if reportPortalClient is null.
 */
void finalizeReportPortal(ReportPortalClient reportPortalClient) {
  // Validate input parameter
  if (!reportPortalClient) {
    throw new IllegalArgumentException("ReportPortalClient must be provided and cannot be null.")
  }

  stage("[ReportPortal] Finish run") {
    try {
      def response = reportPortalClient.launchFinish()
      echo "${response}"
    } catch (Exception e) {
      echo "Couldn't stop run in ReportPortal\nError: ${e.getMessage()}"
    }
  }
}

/**
 * Unpacks Allure reports for the specified result paths.
 *
 * This function unstashes and unzips the report files from the specified result paths.
 *
 * @param resultPaths The list of paths where reports are stored. Must not be empty.
 * @throws IllegalArgumentException if resultPaths is null or empty.
 */
void unpackAllureReport(List resultPaths) {
  // Validate input parameter
  if (!resultPaths || resultPaths.isEmpty()) {
    throw new IllegalArgumentException("resultPaths must be provided and cannot be empty.")
  }

  stage('[Stash] Unpack report') {
    for (path in resultPaths) {
      unstash name: path
      unzip zipFile: "${path}.zip", dir: path
    }
  }
}

/**
 * Generates and publishes the Allure report from the test results.
 *
 * This function creates the Allure report based on the specified result paths and publishes it.
 *
 * @param resultPaths The paths where test results are stored. Must not be empty.
 * @throws IllegalArgumentException if resultPaths is null or empty.
 */
void generateAndPublishAllureReport(List resultPaths) {
  // Validate input parameter
  if (!resultPaths || resultPaths.isEmpty()) {
    return
  }

  stage('[Allure] Generate report') {
    def allureHome = tool type: 'allure', name: Constants.CYPRESS_ALLURE_VERSION
    sh "${allureHome}/bin/allure generate --clean ${resultPaths.collect { path -> "${path}/allure-results" }.join(" ")}"
  }

  stage('[Allure] Publish report') {
    allure([includeProperties: false,
            jdk              : '',
            commandline      : Constants.CYPRESS_ALLURE_VERSION,
            properties       : [],
            reportBuildPolicy: 'ALWAYS',
            results          : resultPaths.collect { path -> [path: "${path}/allure-results"] }])
  }
}

/**
 * Analyzes the results of the test run.
 *
 * This function reads the test run results and returns a summary of the execution.
 *
 * @return The updated test run execution summary.
 */
IRunExecutionSummary analyzeResults() {
  IRunExecutionSummary testRunExecutionSummary
  stage('[Report] Analyze results') {
    String suitesPath = "${env.WORKSPACE}/allure-report/data/suites.json"
    String categoriesPath = "${env.WORKSPACE}/allure-report/data/categories.json"

    def jsonSuites = fileExists(suitesPath) ? readJSON(file: suitesPath) : [:]
    def jsonDefects = fileExists(categoriesPath) ? readJSON(file: categoriesPath) : [:]

    testRunExecutionSummary = CypressRunExecutionSummary.addFromJSON(jsonSuites)
    testRunExecutionSummary.addDefectsFromJSON(jsonDefects)
  }
  return testRunExecutionSummary
}

/**
 * Sends notifications to Slack if required.
 *
 * This function sends notifications to a specified Slack channel based on the test run summary.
 *
 * @param sendSlackNotification Indicates whether to send Slack notifications.
 * @param testRunExecutionSummary The summary of the test run execution. Must not be null.
 * @param ciBuildId The name of the build. Must not be null or empty.
 * @param useReportPortal Indicates whether Report Portal is used.
 * @param channel The Slack channel to send notifications to. Defaults to '#rancher_tests_notifications'.
 */
void sendNotifications(IRunExecutionSummary testRunExecutionSummary, String ciBuildId, boolean useReportPortal,
                       channel = '#rancher_tests_notifications') {
  // Validate input parameters
  if (!testRunExecutionSummary || !ciBuildId) {
    throw new IllegalArgumentException("testRunExecutionSummary and ciBuildId must be provided and cannot be null or empty.")
  }

  stage('[Slack] Send notification') {
    slackSend(attachments: folioSlackNotificationUtils
      .renderBuildAndTestResultMessage(TestType.CYPRESS,
        testRunExecutionSummary,
        ciBuildId,
        useReportPortal,
        "${env.BUILD_URL}allure/"),
      channel: channel)
  }
}

@SuppressWarnings('GrMethodMayBeStatic')
/**
 * Generates a random alphanumeric string of the specified length.
 *
 * @param length The desired length of the generated ID. Must be greater than zero.
 * @return A random alphanumeric string.
 * @throws IllegalArgumentException if the length is less than or equal to zero.
 */
String generateRandomId(int length) {
  // Validate input parameter
  if (length <= 0) {
    throw new IllegalArgumentException("Length must be greater than zero.")
  }

  // Define the character pool
  def chars = ('a'..'z') + ('0'..'9')
  Random random = new Random()

  // Generate the random ID
  return (1..length).collect { chars[random.nextInt(chars.size())] }.join()
}
