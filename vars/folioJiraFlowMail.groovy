void sendMailNotification (List emails, String body, String subject) {
  String pipelineInfo = """
  <p>Build Status: ${currentBuild.currentResult}</p>
  <p>Build Number: ${currentBuild.number}</p>
  <p>Build URL: ${BUILD_URL}</p>
  """
  emails.each { mail ->
    emailext body: """${pipelineInfo}\n${body}""", subject: "${subject}", to: "${mail}"
  }
}
