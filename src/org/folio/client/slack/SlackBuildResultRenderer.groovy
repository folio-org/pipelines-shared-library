package org.folio.client.slack

import com.cloudbees.groovy.cps.NonCPS
import org.folio.client.slack.templates.SlackBuildResultTemplates

enum SlackBuildResultRenderer {
  SUCCESS("good", SlackBuildResultTemplates.SUCCESS_TEXT)
  , UNSTABLE("#E9D502", SlackBuildResultTemplates.UNSTABLE_TEXT)
  , FAILURE("#FF0000", SlackBuildResultTemplates.FAILED_TEXT)
  , NOT_BUILT("#FF0000", SlackBuildResultTemplates.FAILED_TEXT)
  , ABORTED("#FF0000", SlackBuildResultTemplates.FAILED_TEXT)

  final String color
  final String textTemplate

  private SlackBuildResultRenderer(String color, String textTemplate) {
    this.color = color
    this.textTemplate = textTemplate
  }

  String renderSection(Map<String, String> textParams, String buildUrl){
    String text = SlackHelper.fillTemplate(textParams, textTemplate)

    String action = SlackHelper.renderAction(buildUrl, SlackBuildResultTemplates.ACTION_TEXT)

    return SlackHelper.renderSection("", text, color, [action])
  }

  String renderSection(String jobName, String buildNumber, String stageName, String buildUrl){
    return renderSection(getTextParams(jobName, buildNumber, stageName), buildUrl)
  }

  @NonCPS
  static SlackBuildResultRenderer fromResult(String result) throws Error{
    for(SlackBuildResultRenderer elem: values()){
      if(elem.name() == result){
        return elem
      }
    }
    throw new Error("Unknown build result")
  }

  @NonCPS
  static Map<String, String> getTextParams(String jobName, String buildNumber, String stageName){
    [
      'BUILD_TXT_JOB_NAME' : jobName
      , 'BUILD_TXT_BUILD_NUMBER': buildNumber
      , 'BUILD_TXT_STAGE_NAME': stageName
    ]
  }
}
