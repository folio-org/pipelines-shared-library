import org.folio.client.reportportal.ReportPortalClient
import org.folio.jenkins.PodTemplates
import org.folio.models.parameters.CypressTestsParameters
import org.folio.testing.TestType
import org.folio.testing.cypress.results.CypressRunExecutionSummary
import org.folio.utilities.Logger

//TODO: Move to folioCypressFlow.groovy once verified

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
 * Executes the Cypress test flow.
 *
 * This function validates the input parameters, sets up the Report Portal client if required,
 * and orchestrates the Cypress tests execution workflow using batch-based parallel execution.
 * It performs the following steps:
 *
 *   1. Validates required parameters.
 *   2. Initializes a Report Portal client (if reporting is enabled) and configures execution parameters.
 *   3. Clones the Cypress repository and sets up common environment variables.
 *   4. Compiles the Cypress tests and generates execution batches for optimized parallel processing.
 *   5. Archives the tests and stashes the archive for parallel execution.
 *   6. Configures parallel workers based on execution batches to:
 *       - Unstash and extract the archived tests.
 *       - Execute the tests with batch-specific parameters.
 *       - Archive the test results.
 *   7. Applies timeout to the entire parallel execution workflow.
 *   8. Merges results from all workers.
 *   9. Finalizes Report Portal reporting (if enabled), unpacks the Allure report,
 *      generates and publishes the Allure report, and analyzes the test run.
 *
 * @param ciBuildId The CI build identifier.
 * @param testsToRun A list of CypressTestsParameters containing test execution details.
 * @param sendNotification Flag to indicate if notifications should be sent after execution.
 *                              Defaults to false.
 * @param reportPortalUse Flag to enable Report Portal integration.
 *                              Defaults to false.
 * @param reportPortalRunType Specifies the Report Portal run type; applicable when integration is enabled.
 * @return An CypressRunExecutionSummary instance summarizing the test execution.
 * @throws Exception            Propagates any exceptions encountered during the execution flow.
 */
CypressRunExecutionSummary call(String ciBuildId, List<CypressTestsParameters> testsToRun, boolean sendNotification = false,
                                boolean reportPortalUse = false, String reportPortalRunType = '') {
  folioCypress.validateParameter(ciBuildId, 'ciBuildId')
  folioCypress.validateParameter(testsToRun, 'testsToRun')

  PodTemplates podTemplates = new PodTemplates(this, true)
  Logger logger = new Logger(this, 'folioCypressFlow.groovy')

  CypressRunExecutionSummary testRunExecutionSummary
  ReportPortalClient reportPortalClient = null
  String reportPortalExecParameters = ''

  List allureResultsList = []

  try {
    testsToRun.each { CypressTestsParameters testParams ->
      List execBatches = []

      podTemplates.cypressAgent {
        if (reportPortalUse) {
          folioCypress.validateParameter(reportPortalRunType, 'reportPortalRunType')

          reportPortalClient = new ReportPortalClient(this,
            TestType.CYPRESS,
            ciBuildId,
            env.BUILD_NUMBER,
            env.WORKSPACE,
            reportPortalRunType)

          reportPortalExecParameters = folioCypress.setupReportPortal(reportPortalClient)
        }

        container('cypress') {
          testParams.addExecParameter(reportPortalExecParameters)

          folioCypress.cloneCypressRepo(testParams.testsSrcBranch)

          folioCypress.setupCommonEnvironmentVariables(testParams.tenantUrl,
            testParams.okapiUrl,
            testParams.tenant.tenantId,
            testParams.tenant.adminUser.username,
            testParams.tenant.adminUser.getPasswordPlainText())

          folioCypress.compileCypressTests()

          execBatches = folioCypress.compileExecBatches(testParams.compileExecParameters, testParams.numberOfWorkers)

          if (testParams.prepare) {
            folioCypress.prepareTenantForCypressTests(testParams)
          }

          stage('[Stash] Archive tests') {
            sh """
            touch ${cypressStash('archive')}
            tar --exclude=${cypressStash('archive')} -zcf ${cypressStash('archive')} .
            md5sum ${cypressStash('archive')} > ${cypressStash('checksum')}
          """.stripIndent()
            stash(name: cypressStash('name'),
              includes: "${cypressStash('archive')},${cypressStash('checksum')}")
          }
        }
      }

      stage('Run tests') {
        def workers = [failFast: false]
        String runId = folioCypress.generateRandomId(3)
        testParams.ciBuildId = "${ciBuildId}-${runId}"
        List localAllureResults = []

        execBatches.size().times { int batchIndex ->
          String workerId = "${runId}${batchIndex}"
          workers["Worker#${workerId}"] = {
            podTemplates.cypressAgent {
              container('cypress') {
                stage('[Stash] Extract tests') {
                  unstash name: cypressStash('name')
                  sh """
                    md5sum -c ${cypressStash('checksum')}
                    tar -zxf ${cypressStash('archive')}
                    rm -rf ${cypressStash('archive')} ${cypressStash('checksum')}
                  """
                }

                folioCypress.setupCommonEnvironmentVariables(testParams.tenantUrl,
                  testParams.okapiUrl,
                  testParams.tenant.tenantId,
                  testParams.tenant.adminUser.username,
                  testParams.tenant.adminUser.getPasswordPlainText())

                String execParameters = "${execBatches[batchIndex]} ${testParams.execParameters}"

                folioCypress.executeTests(testParams.ciBuildId,
                  testParams.browserName,
                  execParameters,
                  testParams.testrailProjectID,
                  testParams.testrailRunID)

                localAllureResults.add(folioCypress.archiveTestResults(workerId))
              }
            }
          }
        }

        timeout(time: testParams.timeout, unit: 'MINUTES') {
          parallel(workers)
        }
        allureResultsList.addAll(localAllureResults)
      }

      //TODO: Remove debug log after verification
      logger.debug("Allure results: ${allureResultsList}")
    }
  } catch (Exception e) {
    logger.error("Error during Cypress tests execution: ${e.getMessage()}")
  } finally {
    podTemplates.rancherJavaAgent {
      if (reportPortalUse && reportPortalClient != null) {
        folioCypress.finalizeReportPortal(reportPortalClient)
      }

      if (!allureResultsList.isEmpty()) {
        folioCypress.unpackAllureReport(allureResultsList)

        container('java') {
          folioCypress.generateAndPublishAllureReport(allureResultsList)
        }
      }

      testRunExecutionSummary = folioCypress.analyzeResults()

      try {
        if (sendNotification) {
          folioCypress.sendNotifications(testRunExecutionSummary, ciBuildId, reportPortalUse)
        }
      } catch (Exception e) {
        logger.warning("Error sending notifications: ${e.getMessage()}")
      }
    }
  }
  return testRunExecutionSummary
}
