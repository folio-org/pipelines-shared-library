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

void notifyKitFox() {
  List emails = ["eldiiar_duishenaliev@epam.com", "guram_jalaghonia@epam.com", "oleksandr_haimanov@epam.com",
                 "renat_safiulin@epam.com", "vasili_kapylou@epam.com"]
  if (!("${currentBuild.currentResult}" in ["UNSTABLE", "SUCCESS", "ABORTED"])) {
    sendMailNotification(emails, "Pipeline failure", "Please review information above")
  }
}
