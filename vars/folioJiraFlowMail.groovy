void sendMailNotification(List emails, String body, String subject) {
  String pipelineInfo = """
Build Status: ${currentBuild.currentResult}
Build Number: ${currentBuild.number}
Build URL: ${BUILD_URL}
  """
  emails.each { mail ->
    emailext body: """${pipelineInfo}\n${body}""", subject: "${subject}", to: "${mail}"
  }
}
