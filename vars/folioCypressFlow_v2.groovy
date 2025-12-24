import org.folio.client.reportportal.ReportPortalClient
import org.folio.jenkins.PodTemplates
import org.folio.models.parameters.CypressTestsParameters
import org.folio.testing.TestType
import org.folio.testing.cypress.results.CypressRunExecutionSummary
import org.folio.utilities.Logger

CypressRunExecutionSummary call(String ciBuildId, List<CypressTestsParameters> testsToRun, boolean sendNotification = false,
                                boolean reportPortalUse = false, String reportPortalRunType = '') {
  folioCypress.validateParameter(ciBuildId, "ciBuildId")
  folioCypress.validateParameter(testsToRun, "testsToRun")

  PodTemplates podTemplates = new PodTemplates(this, true)
  Logger logger = new Logger(this, 'folioCypressFlow.groovy')
  ReportPortalClient reportPortalClient = null

  podTemplates.cypressAgent {
    if (reportPortalUse) {
      folioCypress.validateParameter(reportPortalRunType, "reportPortalRunType")

      reportPortalClient = new ReportPortalClient(this,
        TestType.CYPRESS,
        ciBuildId,
        env.BUILD_NUMBER,
        env.WORKSPACE,
        reportPortalRunType)

      reportPortalExecParameters = folioCypress.setupReportPortal(reportPortalClient)
    }

    container('cypress') {
      logger.info("Starting test execution flow...")
    }
  }

  podTemplates.rancherJavaAgent {
    if (reportPortalUse && reportPortalClient != null) {
      folioCypress.finalizeReportPortal(reportPortalClient)
    }
  }
}
