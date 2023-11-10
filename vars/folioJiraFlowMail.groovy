import org.folio.rest_v2.Common

void sendMailNotification(List emails, String subject, String body) {
  String pipelineInfo = """
Pipeline: ${JOB_NAME}
Build Status: ${currentBuild.currentResult}
Build Number: ${currentBuild.number}
Build URL: ${BUILD_URL}
  """
  emails.each { mail ->
    try {
      emailext body: """${pipelineInfo}\n${body}""", subject: "${subject}", to: "${mail}"
      new Common(this, "https://fakeUrl").logger.info("Mail sent to: ${mail}")
    } catch (Exception e) {
      new Common(this, "https://fakeUrl").logger.warning("Unable to send email to: ${mail}\nError: ${e.getMessage()}")
    }
  }
}
