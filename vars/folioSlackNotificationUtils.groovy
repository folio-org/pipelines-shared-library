import org.folio.client.slack.SlackBuildResultRenderer

String renderSlackBuildResultMessage(){
  println("I'm in folioSlackNotificationUtils.groovy")
  println("I'm in folioSlackNotificationUtils.groovy. currentBuild=${currentBuild}.")
  SlackBuildResultRenderer slackBuildResult = SlackBuildResultRenderer.fromResult(currentBuild.result)

  println("I'm in folioSlackNotificationUtils.groovy. SlackBuildResultRenderer was got.")

  return slackBuildResult.renderSection(
      env.JOB_NAME
      , env.BUILD_NUMBER
      , STAGE_NAME
      , "${env.BUILD_URL}console"
    )
}
