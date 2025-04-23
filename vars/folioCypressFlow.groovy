import org.folio.Constants
import org.folio.client.reportportal.ReportPortalClient
import org.folio.jenkins.JenkinsAgentLabel
import org.folio.jenkins.PodTemplates
import org.folio.models.parameters.CypressTestsParameters
import org.folio.testing.IRunExecutionSummary
import org.folio.testing.TestType

/**
 * Retrieves the Cypress stash configuration.
 *
 * @param key Optional parameter. If provided, returns the value corresponding to the key in the stash map.
 *            Valid keys include: \`name\` for the test stash name, \`archive\` for the archive filename, and \`checksum\` for the checksum filename.
 * @return The value from the stash map if a key is provided; otherwise, returns the full stash map.
 */
private static def cypressStash(String key = null) {
  def stashMap = [name: 'cypress-tests', archive: 'cypress-tests.tar.gz', checksum: 'cypress-tests.tar.gz.md5']
  if (key) {
    return stashMap[key]
  }
  return stashMap
}

/**
 * Wrapper for the node block with Cypress agent pod template
 * @param body
 */
void nodeWrapper(Closure body) {
  PodTemplates podTemplates = new PodTemplates(this, true)

  podTemplates.cypressTemplate {
    node(JenkinsAgentLabel.CYPRESS_AGENT.getLabel()) {
      container('cypress') {
        body()
      }
    }
  }
}

/**
 * Executes the Cypress test flow.
 *
 * This function validates the input parameters, sets up the Report Portal client if required,
 * and orchestrates the Cypress tests execution workflow. It performs the following steps:
 *
 *   1. Validates required parameters.
 *   2. Initializes a Report Portal client (if reporting is enabled) and configures execution parameters.
 *   3. Clones the Cypress repository and sets up common environment variables.
 *   4. Compiles the Cypress tests.
 *   5. Archives the tests and stashes the archive for parallel execution.
 *   6. Configures parallel workers to:
 *       - Unstash and extract the archived tests.
 *       - Execute the tests with specified timeout settings.
 *       - Archive the test results.
 *   7. Merges results from all workers.
 *   8. Finalizes Report Portal reporting (if enabled), unpacks the Allure report,
 *      generates and publishes the Allure report, and analyzes the test run.
 *
 * @param ciBuildId The CI build identifier.
 * @param testsToRun A list of CypressTestsParameters containing test execution details.
 * @param sendNotification Flag to indicate if notifications should be sent after execution.
 *                              Defaults to false.
 * @param reportPortalUse Flag to enable Report Portal integration.
 *                              Defaults to false.
 * @param reportPortalRunType Specifies the Report Portal run type; applicable when integration is enabled.
 * @return An IRunExecutionSummary instance summarizing the test execution.
 * @throws Exception            Propagates any exceptions encountered during the execution flow.
 */
IRunExecutionSummary call(String ciBuildId, List<CypressTestsParameters> testsToRun, boolean sendNotification = false,
                          boolean reportPortalUse = false, String reportPortalRunType = '') {
  folioCypress.validateParameter(ciBuildId, "ciBuildId")
  folioCypress.validateParameter(testsToRun, "testsToRun")

  List allureResultsList = []
  String reportPortalExecParameters = ''
  ReportPortalClient reportPortalClient = null

  // Initialize Report Portal client if needed and set up execution parameters
  if (reportPortalUse) {
    reportPortalClient = new ReportPortalClient(this,
      TestType.CYPRESS,
      ciBuildId,
      env.BUILD_NUMBER,
      env.WORKSPACE,
      reportPortalRunType)
    reportPortalExecParameters = folioCypress.setupReportPortal(reportPortalClient)
  }

  try {
    echo "Starting test execution flow..."

    testsToRun.each { CypressTestsParameters testParams ->
      testParams.addExecParameter(reportPortalExecParameters)

      nodeWrapper {
        // Clone repository with tests
        folioCypress.cloneCypressRepo(testParams.testsSrcBranch)
        // Set up common environment variables
        folioCypress.setupCommonEnvironmentVariables(testParams.tenantUrl,
          testParams.okapiUrl,
          testParams.tenant.tenantId,
          testParams.tenant.adminUser.username,
          testParams.tenant.adminUser.getPasswordPlainText())
        // Compile tests
        folioCypress.compileCypressTests()

        if (testParams.prepare) {
          folioCypress.prepareTenantForCypressTests(testParams)
        }

        stage('[Stash] Archive tests') {
          // Archive tests for parallel
          sh """
            touch ${cypressStash('archive')}
            tar --exclude=${cypressStash('archive')} -zcf ${cypressStash('archive')} .
            md5sum ${cypressStash('archive')} > ${cypressStash('checksum')}
          """.stripIndent()
          // Stash tests for parallel
          stash(name: cypressStash('name'),
            includes: "${cypressStash('archive')},${cypressStash('checksum')}")
        }
      }
      // Set up parallel workers for executing tests
      def workers = [failFast: false]
      String runId = folioCypress.generateRandomId(3)
      testParams.ciBuildId = "${ciBuildId}-${runId}"
      // Use a local list to collect Allure results per test to avoid concurrent modification issues
      List localAllureResults = []
      // Run tests in parallel with the specified number of workers and timeout for each worker
      testParams.numberOfWorkers.times { int workerIndex ->
        String workerId = "${runId}${workerIndex}"
        workers["Worker#${workerId}"] = {
          nodeWrapper {
            stage('[Stash] Extract tests') {
              unstash name: cypressStash('name')
              sh 'ls -al'
              sh """
                md5sum -c ${cypressStash('checksum')}
                tar -zxf ${cypressStash('archive')}
                rm -rf ${cypressStash('archive')} ${cypressStash('checksum')}
              """
            }

            timeout(time: testParams.timeout, unit: 'MINUTES') {
              // Set up common environment variables
              folioCypress.setupCommonEnvironmentVariables(testParams.tenantUrl,
                testParams.okapiUrl,
                testParams.tenant.tenantId,
                testParams.tenant.adminUser.username,
                testParams.tenant.adminUser.getPasswordPlainText())
              // Execute tests with Cypress runner
              folioCypress.executeTests(testParams.ciBuildId,
                testParams.browserName,
                testParams.execParameters,
                testParams.testrailProjectID,
                testParams.testrailRunID)
            }
            // Archive test results for each worker and add to local list for merging later
            localAllureResults.add(folioCypress.archiveTestResults(workerId))
          }
        }
      }

      // Run all workers in parallel and merge their results
      parallel(workers)
      allureResultsList.addAll(localAllureResults)
    }
  } catch (Exception e) {
    echo("Error executing tests: ${e.getMessage()}")
    throw e // Rethrow the exception for further handling if necessary
  } finally {
    PodTemplates podTemplates = new PodTemplates(this, true)

    podTemplates.javaTemplate(Constants.JAVA_LATEST_VERSION) {
      podTemplates.cypressTemplate {
        node(JenkinsAgentLabel.CYPRESS_AGENT.getLabel()) {
          if (reportPortalUse && reportPortalClient != null) {
            folioCypress.finalizeReportPortal(reportPortalClient)
          }
          // Unpack Allure report
          folioCypress.unpackAllureReport(allureResultsList)
          // Generate and publish Allure report
          container('java') {
            folioCypress.generateAndPublishAllureReport(allureResultsList)
          }
          // Analyze results after execution
          IRunExecutionSummary testRunExecutionSummary = folioCypress.analyzeResults()
          try {
            if (sendNotification) {
              // Send notifications based on the execution summary
              folioCypress.sendNotifications(testRunExecutionSummary, ciBuildId, reportPortalUse)
            }
          } catch (Exception e) {
            echo("Error sending notifications: ${e.getMessage()}")
          }

          return testRunExecutionSummary
        }
      }
    }
  }
}
