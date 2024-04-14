import org.folio.client.slack.SlackBuildResultRenderer

String renderSlackBuildResultMessage(){
  SlackBuildResultRenderer slackBuildResult =
    SlackBuildResultRenderer.fromResult(currentBuild.result == null ? "SUCCESS" : currentBuild.result)

  return slackBuildResult.renderSection(
      env.JOB_NAME
      , env.BUILD_NUMBER
      , STAGE_NAME
      , "${env.BUILD_URL}console"
    )
}
