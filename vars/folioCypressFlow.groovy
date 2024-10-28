import org.folio.client.reportportal.ReportPortalClient
import org.folio.models.parameters.CypressTestsParameters
import org.folio.testing.IRunExecutionSummary
import org.folio.testing.TestType

@SuppressWarnings('GrMethodMayBeStatic')
/**
 * Returns the path to the Report Portal execution parameters file.
 *
 * @return The path as a string.
 */
private final String _reportPortalExecPramsPath() {
  return 'rp-exec-parameters.txt'
}

@SuppressWarnings('GrMethodMayBeStatic')
/**
 * Returns the path to the test results JSON file.
 *
 * @return The path as a string.
 */
private final String _testsResultsPath() {
  return 'result-paths.json'
}

/**
 * Manages a JSON file in the Jenkins workspace.
 *
 * If the JSON file exists, it reads the content, adds new elements,
 * and writes the updated list back to the file. If the file does
 * not exist, it creates a new JSON file with the initial content.
 *
 * @param elementsToAdd A list of elements to add to the JSON list.
 */
void updateResultPathsFile(List<String> elementsToAdd) {
  if (!elementsToAdd || elementsToAdd.isEmpty()) {
    return // Exit if no elements are provided
  }

  String jsonFilePath = _testsResultsPath()
  List jsonList = []

  // Check if the JSON file exists in the workspace
  if (fileExists(jsonFilePath)) {
    echo "File '${jsonFilePath}' exists. Reading content..."
    jsonList = readJSON(file: jsonFilePath) // Read and parse the JSON file
    echo "Current list: ${jsonList}"
  } else {
    echo "File '${jsonFilePath}' does not exist. Creating a new one."
  }

  // Add elements to the list
  jsonList.addAll(elementsToAdd)

  // Write the updated JSON content back to the file
  writeJSON(file: jsonFilePath, json: jsonList)

  echo "Updated list written to '${jsonFilePath}': ${jsonList}"
}

@SuppressWarnings('GrMethodMayBeStatic')
/**
 * Retrieves the worker configurations for different Cypress testing scenarios.
 *
 * @return A map containing worker limits and batch sizes for different worker labels.
 */
private final Map<String, Map<String, Integer>> _getWorkerConfigs() {
  return ['cypress-ci'    : ['workerLimit': 4, 'batchSize': 4],
          'cypress-static': ['workerLimit': 6, 'batchSize': 6],
          'cypress'       : ['workerLimit': 12, 'batchSize': 4]]
}

@SuppressWarnings('GrMethodMayBeStatic')
/**
 * Retrieves the worker limit based on the provided worker label.
 *
 * @param workerLabel The label of the worker whose limit is being requested.
 * @return The worker limit associated with the specified worker label.
 * @throws IllegalArgumentException if the worker label is unknown.
 */
private int _getWorkerLimit(String workerLabel) {
  Map<String, Integer> config = _getWorkerConfigs()[workerLabel]
  if (config) {
    return config['workerLimit'] // Return the worker limit
  }
  throw new IllegalArgumentException("Worker agent label unknown: '${workerLabel}'")
}

@SuppressWarnings('GrMethodMayBeStatic')
/**
 * Retrieves the batch size based on the provided worker label.
 *
 * @param workerLabel The label of the worker whose batch size is being requested.
 * @return The batch size associated with the specified worker label.
 * @throws IllegalArgumentException if the worker label is unknown.
 */
private int _getBatchSize(String workerLabel) {
  Map<String, Integer> config = _getWorkerConfigs()[workerLabel]
  if (config) {
    return config['batchSize'] // Return the batch size
  }
  throw new IllegalArgumentException("Worker agent label unknown: '${workerLabel}'")
}

/**
 * Executes Cypress tests in a multi-threaded environment, managing batches of workers.
 *
 * This method sets up the necessary environment variables, divides the workers into batches,
 * and executes tests in parallel, archiving results and generating reports.
 *
 * @param params The parameters for the Cypress test execution.
 */
void multiThreadRun(CypressTestsParameters params) {
  List stashNames = []
  String workerId = folioCypress.generateRandomId(3)
  params.ciBuildId = "multi-thread-${params.ciBuildId}-${workerId}"

  // Read Report Portal execution parameters if the file exists
  if (fileExists(_reportPortalExecPramsPath())) {
    String reportPortalExecParameters = readFile(file: _reportPortalExecPramsPath()).trim()
    params.addExecParameter(reportPortalExecParameters)
  }

  // Set up common environment variables for Cypress execution
  folioCypress.setupCommonEnvironmentVariables(params.tenantUrl,
    params.okapiUrl,
    params.tenant.tenantId,
    params.tenant.adminUser.username,
    params.tenant.adminUser.getPasswordPlainText())

  int workersLimit = _getWorkerLimit(params.workerLabel)
  int batchSize = _getBatchSize(params.workerLabel)
  int maxWorkers = Math.min(params.numberOfWorkers, workersLimit)
  // Ensure the number of workers does not exceed the limit
  List<List<Integer>> batches = (1..maxWorkers).toList().collate(batchSize) // Divide workers into batches

  // Execute each batch of workers
  Map<String, Closure> batchExecutions = [failFast: false]
  batches.eachWithIndex { batch, batchIndex ->
    String batchId = (batchIndex + 1).toString()
    String firstWorkerId = "${workerId}${batch[0]}"
    batchExecutions["Batch#${batchId}"] = {
      node(params.workerLabel) {
        stage("[Cypress] Multi thread run #${batchId}") {
          cleanWs notFailBuild: true

          dir("cypress-${firstWorkerId}") {
            // Clone the Cypress repository and compile tests
            folioCypress.cloneCypressRepo(params.testsSrcBranch)
            folioCypress.compileCypressTests()
          }

          // Execute tests in parallel for each worker in the batch
          Map<String, Closure> parallelWorkers = [failFast: false]
          batch.eachWithIndex { worker, workerIndex ->
            String workerItemId = "${workerId}${worker}"
            if (workerIndex > 0) {
              sh "mkdir -p cypress-${workerItemId}"
              sh "cp -r cypress-${firstWorkerId}/. cypress-${workerItemId}"
            }
            parallelWorkers["Worker#${workerItemId}"] = {
              dir("cypress-${workerItemId}") {
                // Execute tests with timeout
                timeout(time: params.timeout, unit: 'MINUTES') {
                  folioCypress.executeTests(params.ciBuildId,
                    params.browserName,
                    params.execParameters,
                    workerItemId,
                    params.testrailProjectID,
                    params.testrailRunID)
                }

                stashNames.add(folioCypress.archiveTestResults(workerItemId))
              }
            }
          }

          // Run all workers in parallel
          parallel(parallelWorkers)
        }
      }
    }
  }

  // Execute all batch runs
  parallel(batchExecutions)

  // Unpack Allure report and update result paths
  folioCypress.unpackAllureReport(stashNames)
  updateResultPathsFile(stashNames)
}

/**
 * Executes Cypress tests in a single-threaded environment.
 *
 * This method sets up the necessary environment variables, runs the tests,
 * and archives the results.
 *
 * @param params The parameters for the Cypress test execution.
 */
void singleThreadRun(CypressTestsParameters params) {
  String stashName = ''
  String workerId = folioCypress.generateRandomId(3)
  params.ciBuildId = "single-thread-${params.ciBuildId}-${workerId}"

  // Read Report Portal execution parameters if the file exists
  if (fileExists(_reportPortalExecPramsPath())) {
    String reportPortalExecParameters = readFile(file: _reportPortalExecPramsPath()).trim()
    params.addExecParameter(reportPortalExecParameters)
  }

  // Set up common environment variables for Cypress execution
  folioCypress.setupCommonEnvironmentVariables(params.tenantUrl,
    params.okapiUrl,
    params.tenant.tenantId,
    params.tenant.adminUser.username,
    params.tenant.adminUser.getPasswordPlainText())

  node(params.workerLabel) {
    stage('[Cypress] Single thread run') {
      cleanWs notFailBuild: true

      echo "Running tests with worker ID: ${workerId}"
      dir("cypress-${workerId}") {
        // Clone the Cypress repository and compile tests
        folioCypress.cloneCypressRepo(params.testsSrcBranch)
        folioCypress.compileCypressTests()

        timeout(time: params.timeout, unit: 'MINUTES') {
          // Execute tests
          folioCypress.executeTests(params.ciBuildId,
            params.browserName,
            params.execParameters,
            workerId,
            params.testrailProjectID,
            params.testrailRunID)
        }

        // Archive test results
        stashName = folioCypress.archiveTestResults(workerId)
      }
    }
  }

  // Unpack Allure report and update result paths
  folioCypress.unpackAllureReport([stashName])
  updateResultPathsFile([stashName])
}

/**
 * Wrapper for executing tests with optional Report Portal integration.
 *
 * This function initializes the Report Portal client, executes the provided closure,
 * finalizes Report Portal, generates an Allure report, analyzes results, and sends notifications.
 *
 * @param ciBuildId The CI build ID to associate with the test run.
 * @param reportPortalUse A flag indicating whether to use Report Portal for reporting.
 * @param reportPortalRunType The type of run for Report Portal (required if reportPortalUse is true).
 * @param body A closure that contains the test execution logic.
 * @return An IRunExecutionSummary object summarizing the test execution results.
 * @throws IllegalArgumentException if reportPortalRunType is empty when reportPortalUse is true.
 * @throws Exception if an error occurs during test execution.
 */
IRunExecutionSummary runWrapper(String ciBuildId, boolean reportPortalUse = false, String reportPortalRunType = '', boolean sendNotification = true, Closure body) {
  if (reportPortalUse && (reportPortalRunType == null || reportPortalRunType.trim().isEmpty())) {
    throw new IllegalArgumentException("ReportPortal run type could not be empty!")
  }

  // Initialize the Report Portal client
  ReportPortalClient reportPortalClient = new ReportPortalClient(this,
    TestType.CYPRESS,
    ciBuildId,
    env.BUILD_NUMBER,
    env.WORKSPACE,
    reportPortalRunType)

  // Set up Report Portal and gather execution parameters if requested
  if (reportPortalUse) {
    String reportPortalExecParameters = folioCypress.setupReportPortal(reportPortalClient)
    writeFile(file: _reportPortalExecPramsPath(), text: reportPortalExecParameters)
  }

  try {
    echo "Starting test execution flow..."

    // Execute the provided closure containing test logic
    body()

    echo "Test execution flow completed."

  } catch (Exception e) {
    echo "Error executing tests: ${e.message}"
    throw e // Rethrow the exception for further handling if necessary
  } finally {
    if (reportPortalUse) {
      folioCypress.finalizeReportPortal(reportPortalClient)
    }

    // Generate and publish Allure report
    List resultPathsList = fileExists(_testsResultsPath()) ? readJSON(file: _testsResultsPath()) : []
    folioCypress.generateAndPublishAllureReport(resultPathsList)

    // Analyze results after execution
    IRunExecutionSummary testRunExecutionSummary = folioCypress.analyzeResults()

    if (sendNotification) {
      // Send notifications based on the execution summary
      folioCypress.sendNotifications(testRunExecutionSummary, ciBuildId, reportPortalUse)
    }

    return testRunExecutionSummary
  }
}
