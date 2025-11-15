import groovy.json.JsonException
import org.folio.Constants
import org.folio.client.reportportal.ReportPortalClient
import org.folio.models.parameters.CypressTestsParameters
import org.folio.testing.TestType
import org.folio.testing.cypress.results.CypressRunExecutionSummary

/**
 * Validates the specified parameter.
 *
 * This function checks if the parameter is null or empty and throws an exception if it is.
 *
 * @param param The parameter to validate.
 * @param paramName The name of the parameter.
 * @throws IllegalArgumentException if the parameter is null or empty.
 */
void validateParameter(param, String paramName) {
  // Check for null values
  if (param == null) {
    throw new IllegalArgumentException("${paramName} must be provided and cannot be null.")
  }

  // For Strings, ensure they are not empty after trimming
  if (param instanceof String && param.trim().isEmpty()) {
    throw new IllegalArgumentException("${paramName} must be provided and cannot be empty.")
  }

  // For Collections (like List, Set) and Maps, ensure they are not empty
  if ((param instanceof Collection || param instanceof Map) && param.isEmpty()) {
    throw new IllegalArgumentException("${paramName} must be provided and cannot be empty.")
  }

  // For booleans and other types (e.g., CypressRunExecutionSummary), a non-null value is considered valid
}

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
  validateParameter(branch, "Branch name")

  stage('[Git] Checkout Cypress repo') {
    echo("Checking out branch: ${branch}")
    checkout(scmGit(
      branches: [[name: "*/${branch}"]],
      extensions: [cloneOption(depth: 50, noTags: true, reference: '', shallow: true)],
      userRemoteConfigs: [[credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID,
                           url          : Constants.CYPRESS_REPOSITORY_URL]]))
  }
}

/**
 * Sets up common environment variables for Cypress testing.
 *
 * This function sets the environment variables required for Cypress testing.
 *
 * @param tenantUrl The URL of the tenant. Must not be null or empty.
 * @param okapiUrl The URL of the Okapi service. Must not be null or empty.
 * @param tenantId The ID of the tenant. Must not be null or empty.
 * @param adminUsername The username of the admin user. Must not be null or empty.
 * @param adminPassword The password of the admin user. Must not be null or empty.
 * @throws IllegalArgumentException if any of the parameters are null or empty.
 */
void setupCommonEnvironmentVariables(String tenantUrl, String okapiUrl, String tenantId, String adminUsername,
                                     String adminPassword) {
  validateParameter(tenantUrl, "Tenant URL")
  validateParameter(okapiUrl, "Okapi URL")
  validateParameter(tenantId, "Tenant ID")
  validateParameter(adminUsername, "Admin username")
  validateParameter(adminPassword, "Admin password")

  stage('Set env variables') {
    env.CYPRESS_BASE_URL = tenantUrl
    env.CYPRESS_OKAPI_HOST = okapiUrl
    env.CYPRESS_OKAPI_TENANT = tenantId
    env.CYPRESS_diku_login = adminUsername
    env.CYPRESS_diku_password = adminPassword
    env.AWS_DEFAULT_REGION = Constants.AWS_REGION

    echo("Environment variables set for Cypress testing.")
  }
}

void prepareTenantForCypressTests(CypressTestsParameters prepare) {
  stage('[Prepare] Tenant') {
    echo "Preparing tenant for Cypress tests..."
    try {
      sh "set +x; export EHOLDINGS_KB_URL=${prepare.kbUrl}; export EHOLDINGS_KB_ID=${prepare.kbId}; export EHOLDINGS_KB_KEY=${prepare.kbKey}; export OKAPI_HOST=${prepare.okapiUrl}; " +
        "export OKAPI_TENANT=${prepare.tenant.tenantId}; export DIKU_LOGIN=${prepare.tenant.adminUser.username}; export DIKU_PASSWORD=${prepare.tenant.adminUser.getPasswordPlainText()}"
      sh "set -x; node ./scripts/prepare.js"
    } catch (Exception e) {
      currentBuild.result = 'ABORTED'
      throw new Exception("Failed to prepare tenant for Cypress tests: ${e.getMessage()}")
    }
  }
}

/**
 * Compiles the Cypress tests.
 *
 * This function installs the required dependencies and compiles the Cypress tests.
 */
void compileCypressTests() {
  stage('[Yarn] Compile Cypress tests') {
    sh """export HOME=\$(pwd)
      export CYPRESS_CACHE_FOLDER=\$(pwd)/cache

      node -v
      yarn -v

      yarn config set @folio:registry ${Constants.FOLIO_NPM_REPO_URL}
      yarn install --network-timeout 300000
      yarn add -D cypress-testrail-simple@${readPackageJsonDependencyVersion('./package.json', 'cypress-testrail-simple')}
      yarn global add cypress-cloud@${readPackageJsonDependencyVersion('./package.json', 'cypress-cloud')}
    """.stripIndent()
    // Uncomment to add Report Portal agent for Cypress
    // sh "yarn add @reportportal/agent-js-cypress@latest"
  }
}

/**
 * Executes the Cypress tests.
 *
 * This function runs the Cypress tests using the specified parameters.
 *
 * @param ciBuildId The name of the custom build. Must not be null or empty.
 * @param browserName The name of the browser to run tests on. Must not be null or empty.
 * @param execParameters Additional parameters for executing tests. Must not be null.
 * @param testrailProjectID The TestRail project ID. Defaults to an empty string.
 * @param testrailRunID The TestRail run ID. Defaults to an empty string.
 */
void executeTests(String ciBuildId, String browserName, String execParameters,
                  String testrailProjectID = '', String testrailRunID = '') {
  validateParameter(ciBuildId, "ciBuildId")
  validateParameter(browserName, "Browser name")
  validateParameter(execParameters, "Execution parameters")

  stage('[Cypress] Run tests') {
    String execString = createExecString(ciBuildId, browserName, execParameters)
    if (testrailProjectID?.trim() && testrailRunID?.trim()) {
      runTestsWithTestRail(testrailProjectID, testrailRunID, execString)
    } else {
      runTests(execString)
    }
  }
}

/**
 * Creates the command string for executing Cypress tests.
 *
 * This function generates the command string for running Cypress tests.
 *
 * @param browserName The name of the browser to run tests on. Must not be null or empty.
 * @param ciBuildId The name of the custom build. Must not be null or empty.
 * @param execParameters Additional parameters for executing tests. Must not be null.
 * @return The command string for executing tests.
 */
String createExecString(String ciBuildId, String browserName, String execParameters) {
  validateParameter(ciBuildId, "ciBuildId")
  validateParameter(browserName, "Browser name")
  validateParameter(execParameters, "Execution parameters")

  // Generate a random screen ID for Xvfb
  String screenId = (new Random().nextInt(90) + 10).toString()
  return """export HOME=\$(pwd)
    export CYPRESS_CACHE_FOLDER=\$(pwd)/cache
    export DISPLAY=:${screenId}

    mkdir -p /tmp/.X11-unix
    Xvfb \$DISPLAY -screen 0 1920x1080x24 &
    npx cypress-cloud run --parallel --record --browser ${browserName} --ci-build-id ${ciBuildId} ${execParameters}
    pkill Xvfb
  """.stripIndent()
}

/**
 * Runs the specified command string for executing tests.
 *
 * This function executes the specified command string for running tests.
 *
 * @param execString The command string for executing tests. Must not be null or empty.
 */
void runTests(String execString) {
  validateParameter(execString, "Execution string")

  try {
    def numCurl = "curl https://jenkins.ci.folio.org > /dev/null 2>&1"
    sh """nohup bash -c 'for i in \$(seq 1 86400); do sleep 1 && ${numCurl}; done' &"""
    sh execString
  } catch (Exception e) {
    echo("Error executing tests: ${e.getMessage()}")
    currentBuild.result = 'UNSTABLE'
  }
}

/**
 * Runs the tests with TestRail integration.
 *
 * This function runs the tests with TestRail integration and posts the results to TestRail.
 *
 * @param testrailProjectID The TestRail project ID. Must not be null or empty.
 * @param testrailRunID The TestRail run ID. Must not be null or empty.
 * @param execString The command string for executing tests. Must not be null or empty.
 */
void runTestsWithTestRail(String testrailProjectID, String testrailRunID, String execString) {
  validateParameter(testrailProjectID, "TestRail project ID")
  validateParameter(testrailRunID, "TestRail run ID")
  validateParameter(execString, "Execution string")

  execString = """
    export TESTRAIL_HOST=${Constants.CYPRESS_TESTRAIL_HOST}
    export TESTRAIL_PROJECTID=${testrailProjectID}
    export TESTRAIL_RUN_ID=${testrailRunID}
    export CYPRESS_allureReuseAfterSpec=true
  """.stripIndent() + execString

  echo("Test results will be posted to TestRail.\nProjectID: ${testrailProjectID},\nRunID: ${testrailRunID}")

  withCredentials([usernamePassword(credentialsId: Constants.CYPRESS_TESTRAIL_CREDENTIALS_ID,
    passwordVariable: 'TESTRAIL_PASSWORD',
    usernameVariable: 'TESTRAIL_USERNAME')]) {
    runTests(execString)
  }
}

/**
 * Archives the test results for the specified worker.
 * @param workerId
 * @return
 */
String archiveTestResults(String workerId) {
  validateParameter(workerId, "Worker ID")

  stage('[Stash] Archive test results') {
    String stashName = "allure-results-${workerId}"
    String tarName = "allure-results-${workerId}.tar.gz"

    sh """
      tar -zcf ${tarName} allure-results/*
      md5sum ${tarName} > ${tarName}.md5
      test -f ${tarName}
    """

    archiveArtifacts artifacts: "${tarName}, ${tarName}.md5", allowEmptyArchive: true, fingerprint: true, defaultExcludes: false
    stash name: stashName, includes: "${tarName}, ${tarName}.md5"

    echo("Test results archived and stashed: ${stashName}")
    return stashName
  }
}

/**
 * Reads the version of the specified dependency from the package.json file.
 *
 * This function reads the version of the specified dependency from the package.json file.
 *
 * @param filePath The path to the package.json file. Must not be null or empty.
 * @param dependencyName The name of the dependency to read the version for. Must not be null or empty.
 * @return The version of the specified dependency.
 * @throws IllegalArgumentException if the file path or dependency name is null or empty.
 * @throws FileNotFoundException if the specified file does not exist.
 * @throws IOException if an error occurs while reading the file.
 * @throws JsonException if the file is not a valid JSON.
 */
String readPackageJsonDependencyVersion(String filePath, String dependencyName) {
  validateParameter(filePath, "File path")
  validateParameter(dependencyName, "Dependency name")

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

  def version = packageJson['dependencies'][dependencyName] ?: packageJson['devDependencies'][dependencyName]

  if (!version) {
    echo("Dependency '${dependencyName}' not found in package.json.")
  }
  return version
}

/**
 * Sets up the Report Portal session.
 *
 * This function initializes the Report Portal session and returns the execution parameters.
 *
 * @param reportPortalClient The Report Portal client. Must not be null.
 * @return The execution parameters for Report Portal.
 * @throws IllegalArgumentException if reportPortalClient is null.
 */
String setupReportPortal(ReportPortalClient reportPortalClient) {
  validateParameter(reportPortalClient, "Report Portal client")

  stage('[ReportPortal] Config bind & launch') {
    try {
      String rpLaunchID = reportPortalClient.launch()
      echo("Report Portal Launch ID: ${rpLaunchID}")

      String portalExecParams = reportPortalClient.getExecParams()
      echo("Report portal execution parameters: ${portalExecParams}")

      return portalExecParams
    } catch (Exception e) {
      echo("Error during Report Portal setup\nError: ${e.getMessage()}")
    }
  }
}

/**
 * Finalizes the Report Portal session.
 *
 * This function stops the Report Portal session and prints the response.
 *
 * @param reportPortalClient The Report Portal client. Must not be null.
 * @throws IllegalArgumentException if reportPortalClient is null.
 */
void finalizeReportPortal(ReportPortalClient reportPortalClient) {
  validateParameter(reportPortalClient, "Report Portal client")

  stage("[ReportPortal] Finish run") {
    try {
      def response = reportPortalClient.launchFinish()
      echo("${response}")
    } catch (Exception e) {
      echo("Couldn't stop run in ReportPortal\nError: ${e.getMessage()}")
    }
  }
}

/**
 * Unpacks the Allure report from the specified stashes.
 *
 * This function unpacks the Allure report from the specified stashes.
 *
 * @param stashesList The list of stashes to unpack. Must not be null or empty.
 * @throws IllegalArgumentException if stashesList is null or empty.
 */
void unpackAllureReport(List stashesList) {
  validateParameter(stashesList, "Result paths")

  stage('[Stash] Unpack report') {
    for (stashName in stashesList) {
      unstash name: stashName
      sh "tar --one-top-level=${stashName} -zxf ${stashName}.tar.gz"
    }
  }
}

/**
 * Generates and publishes the Allure report.
 *
 * This function generates and publishes the Allure report from the specified result paths.
 *
 * @param resultPaths The list of result paths to generate the report from. Must not be null or empty.
 * @throws IllegalArgumentException if resultPaths is null or empty.
 */
void generateAndPublishAllureReport(List resultPaths) {
  validateParameter(resultPaths, 'Result paths')

  stage('[Allure] Generate report') {
    def allureHome = tool type: 'allure', name: Constants.CYPRESS_ALLURE_VERSION
    // Set Java heap size to GB and configure ForkJoinPool to prevent OutOfMemoryError during report generation reported by Ostap in RANCHER-2546
    sh "JAVA_TOOL_OPTIONS='-Xmx6G -Xms2G -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -XX:MaxDirectMemorySize=1G -Djava.util.concurrent.ForkJoinPool.common.parallelism=2' ${allureHome}/bin/allure generate --clean ${resultPaths.collect { path -> "${path}/allure-results" }.join(" ")}"
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
 * Analyzes the test results and generates the test run summary.
 *
 * This function analyzes the test results and generates the test run summary.
 *
 * @return The test run summary.
 */
CypressRunExecutionSummary analyzeResults() {
  stage('[Report] Analyze results') {
    CypressRunExecutionSummary testRunExecutionSummary
    String suitesPath = "${env.WORKSPACE}/allure-report/data/suites.json"
    String categoriesPath = "${env.WORKSPACE}/allure-report/data/categories.json"

    Map jsonSuites = fileExists(suitesPath) ? readJSON(file: suitesPath) : [:]
    Map jsonDefects = fileExists(categoriesPath) ? readJSON(file: categoriesPath) : [:]

    testRunExecutionSummary = CypressRunExecutionSummary.addFromJSON(jsonSuites)
    testRunExecutionSummary.addDefectsFromJSON(jsonDefects)
    return testRunExecutionSummary
  }
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
void sendNotifications(CypressRunExecutionSummary testRunExecutionSummary, String ciBuildId, boolean useReportPortal,
                       String channel = '#rancher_tests_notifications') {
  validateParameter(testRunExecutionSummary, "Test run execution summary")
  validateParameter(ciBuildId, "CI build ID")
  validateParameter(useReportPortal, "Use Report Portal")

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

/**
 * Generates a random ID of the specified length.
 *
 * This function generates a random ID of the specified length.
 *
 * @param length The length of the random ID. Must be greater than 0.
 * @return The generated random ID.
 * @throws IllegalArgumentException if the length is less than or equal to 0.
 */
String generateRandomId(int length) {
  validateParameter(length, "ID length")

  // Define the character pool
  def chars = ('a'..'z') + ('0'..'9')
  Random random = new Random()

  // Generate the random ID
  return (1..length).collect { chars[random.nextInt(chars.size())] }.join()
}
