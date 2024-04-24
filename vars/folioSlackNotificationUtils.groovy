import org.folio.client.reportportal.ReportPortalTestType
import org.folio.slack.SlackBuildResultRenderer
import org.folio.slack.SlackHelper
import org.folio.slack.SlackTestResultRenderer
import org.folio.testing.TestExecutionResult
import org.folio.testing.TestType

String renderSlackBuildResultMessageSection(){
  SlackBuildResultRenderer slackBuildResult =
    SlackBuildResultRenderer.fromResult(currentBuild.result == null ? "SUCCESS" : currentBuild.result)

  return renderSlackBuildResultMessageSection(slackBuildResult)
}

String renderSlackBuildResultMessageSection(SlackBuildResultRenderer buildResult){
  return buildResult.renderSection(
    env.JOB_NAME
    , env.BUILD_NUMBER
    , STAGE_NAME
    , "${env.BUILD_URL}console"
  )
}

String renderSlackFailedResultMessage(){
  return SlackHelper
    .renderMessage([renderSlackBuildResultMessageSection(SlackBuildResultRenderer.FAILURE)])
}

String renderSlackTestResultMessageSection(TestType type, LinkedHashMap<String, Integer> testResults
                                           , String buildName, boolean useReportPortal, String url){

  def totalTestsCount = testResults.passed + testResults.failed + testResults.broken
  def passRateInDecimal = totalTestsCount > 0 ? (testResults.passed * 100) / totalTestsCount : 0
  def passRate = passRateInDecimal.intValue()

  println "Total passed tests: ${testResults.passed}"
  println "Total failed tests: ${testResults.failed}"
  println "Total broken tests: ${testResults.broken}"

  SlackTestResultRenderer slackTestType =
    SlackTestResultRenderer.fromType(type, passRate > 50 ? TestExecutionResult.SUCCESS : TestExecutionResult.FAILED)

  return slackTestType.renderSection(
    "${buildName}"
    , "${testResults.passed}"
    , "${testResults.broken}"
    , "${testResults.failed}"
    , "${passRate}"
    , "${url}"
    , useReportPortal
    , ReportPortalTestType.fromType(type).reportPortalLaunchesURL())
}

String renderSlackTestResultMessage(TestType type, LinkedHashMap<String, Integer> testResults
                                    , String buildName, boolean useReportPortal, String url){
  return SlackHelper.renderMessage(
    [
      renderSlackBuildResultMessageSection()
      , renderSlackTestResultMessageSection(type, testResults, buildName, useReportPortal, url)
    ]
  )
}


