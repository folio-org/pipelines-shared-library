import org.folio.Constants
import org.folio.rest_v2.Common
import org.folio.utilities.Logger

void sendMailNotification(List emails, String subject, String body) {
  Logger logger = new Logger(this, 'common')
  String pipelineInfo = """
Pipeline: ${JOB_NAME}
Build Status: ${currentBuild.currentResult}
Build Number: ${currentBuild.number}
Build URL: ${BUILD_URL}
  """
  emails.each { mail ->
    try {
      emailext body: """${pipelineInfo}\n${body}""", subject: "${subject}", to: "${mail}"
      logger.info("Mail sent to: ${mail}")
    } catch (Exception e) {
      logger.warning("Unable to send email to: ${mail}\nError: ${e.getMessage()}")
    }
  }
}

void notifyKitFox() {
  List emails = Constants.KITFOX_MEMBERS
  if (!("${currentBuild.currentResult}" in ["UNSTABLE", "SUCCESS", "ABORTED"])) {
    sendMailNotification(emails, "Pipeline failure", "Please review information above")
  }
}
