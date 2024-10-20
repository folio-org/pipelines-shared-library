import org.folio.client.reportportal.ReportPortalClient
import org.folio.models.parameters.CypressTestsParameters
import org.folio.testing.IRunExecutionSummary
import org.folio.testing.TestType

List<String> runMultiThread(CypressTestsParameters params, String reportPortalExecParameters = '') {
  List allureResultPaths = []
  String workerId = folioCypress.generateRandomId(3)
  params.execParameters += reportPortalExecParameters
  params.ciBuildId = "multi-thread-${params.ciBuildId}-${workerId}"

  int workersLimit
  int batchSize
  switch (params.workerLabel) {
    case 'cypress-ci':
      workersLimit = 4
      batchSize = 4
      break
    case 'cypress-static':
      workersLimit = 6
      batchSize = 6
      break
    case 'cypress':
      workersLimit = 12
      batchSize = 4
      break
    default:
      error("Worker agent label unknown! '${params.workerLabel}'")
      break
  }
  int maxWorkers = Math.min(params.numberOfWorkers, workersLimit) // Ensuring not more than limited workers number
  println("maxWorkers: ${maxWorkers}")
  List<List<Integer>> batches = (1..maxWorkers).toList().collate(batchSize)
  println("batches: ${batches.dump()}")

  // Set up common environment variables
  folioCypress.setupCommonEnvironmentVariables(params.tenantUrl,
    params.okapiUrl,
    params.tenant.tenantId,
    params.tenant.adminUser.username,
    params.tenant.adminUser.getPasswordPlainText())

  // Divide workers into batches
  Map<String, Closure> batchExecutions = [failFast: false]
  batches.eachWithIndex { batch, batchIndex ->
    println("batch: ${batch}")
    println("batchIndex: ${batchIndex}")
    batchExecutions["Batch#${batchIndex + 1}"] = {
      node(params.workerLabel) {
        stage("[Cypress] Multi thread run #${batchIndex + 1}") {
          cleanWs notFailBuild: true

          dir("cypress-${batch[0]}") {
            // Clone the Cypress repository
            folioCypress.cloneCypressRepo(params.testsSrcBranch)
            // Compile Cypress tests
            folioCypress.compileCypressTests()
          }

          batch.eachWithIndex { copyBatch, copyBatchIndex ->
            if (copyBatchIndex > 0) {
              sh "mkdir -p cypress-${copyBatch}"
              sh "cp -r cypress-${batch[0]}/. cypress-${copyBatch}"
            }
          }

          Map<String, Closure> parallelWorkers = [failFast: false]
          batch.each { workerNumber ->
            parallelWorkers["Worker#${workerNumber}"] = {
              dir("cypress-${workerNumber}") {
                // Execute tests
                println("${workerId}${workerNumber}")
//                folioCypress.executeTests(params.ciBuildId,
//                  params.browserName,
//                  params.execParameters,
//                  params.testrailProjectID,
//                  params.testrailRunID,
//                  "${workerId}${workerNumber}")
              }
            }
          }

          println("parallelWorkers: ${parallelWorkers.dump()}")
          parallel(parallelWorkers)

//          batch.each { workerNumber ->
//            dir("cypress-${workerNumber}") {
//              allureResultPaths.add(folioCypress.archiveTestResults("${workerId}${workerNumber}"))
//            }
//          }
        }
      }
    }
  }

  timeout(time: params.timeout, unit: 'MINUTES') {
    println("batchExecutions: ${batchExecutions.dump()}")
    parallel(batchExecutions)
  }

  input('Test')
  return allureResultPaths
}

String runSingleThread(CypressTestsParameters params, String reportPortalExecParameters = '') {
  String allureResultPath = ''
  String workerId = folioCypress.generateRandomId(3)
  params.execParameters += reportPortalExecParameters
  params.ciBuildId = "single-thread-${params.ciBuildId}-${workerId}"

  node(params.workerLabel) {
    stage('[Cypress] Single thread run') {
      cleanWs notFailBuild: true

      echo "Running tests with worker ID: ${workerId}"

      // Clone the Cypress repository
      folioCypress.cloneCypressRepo(params.testsSrcBranch)

      // Set up common environment variables
      folioCypress.setupCommonEnvironmentVariables(params.tenantUrl,
        params.okapiUrl,
        params.tenant.tenantId,
        params.tenant.adminUser.username,
        params.tenant.adminUser.getPasswordPlainText())

      // Compile Cypress tests
      folioCypress.compileCypressTests()

      timeout(time: params.timeout, unit: 'MINUTES') {
        // Execute tests
        folioCypress.executeTests(params.ciBuildId,
          params.browserName,
          params.execParameters,
          params.testrailProjectID,
          params.testrailRunID,
          workerId)
      }

      // Archive test results
      allureResultPath = folioCypress.archiveTestResults(workerId)
    }
  }

  folioCypress.unpackAllureReport([allureResultPath])
}

/**
 * Wrapper for executing tests with optional Report Portal integration.
 *
 * This function sets up the Report Portal client, executes the provided closure,
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
IRunExecutionSummary runWrapper(String ciBuildId, boolean reportPortalUse = false, String reportPortalRunType = '', Closure body) {
    if (reportPortalUse && (reportPortalRunType == null || reportPortalRunType.trim().isEmpty())) {
      throw new IllegalArgumentException("ReportPortal run type could not be empty!")
    }

    String reportPortalExecParameters = ''
    List resultPathsList = []

    // Initialize the Report Portal client
    ReportPortalClient reportPortalClient = new ReportPortalClient(this,
      TestType.CYPRESS,
      ciBuildId,
      env.BUILD_NUMBER,
      env.WORKSPACE,
      reportPortalRunType)

    if (reportPortalUse) {
      // Set up Report Portal (consider checking if this value is used)
      reportPortalExecParameters = folioCypress.setupReportPortal(reportPortalClient)
    }

//  try {
    echo "Starting test execution flow..."

    // Set up a binding for the closure to access shared variables
    binding.setVariable('reportPortalExecParameters', reportPortalExecParameters)
    body()

    echo "Test execution flow completed."
//  } catch (Exception e) {
//    echo "Error executing tests: ${e.message}"
//    throw e // Rethrow the exception for further handling if necessary
//  } finally {
//    if (reportPortalUse) {
//      folioCypress.finalizeReportPortal(reportPortalClient)
//    }
//
//    // Generate and publish Allure report
//    folioCypress.generateAndPublishAllureReport(resultPathsList)
//
//    // Analyze results
//    IRunExecutionSummary testRunExecutionSummary = folioCypress.analyzeResults()
//
//    // Send notifications
//    folioCypress.sendNotifications(testRunExecutionSummary, ciBuildId, reportPortalUse, '#rancher-test-notifications')
//
//    return testRunExecutionSummary
//  }
}
