package org.folio.slack.templates

class SlackBuildResultTemplates {

  private SlackBuildResultTemplates(){}

  static final SUCCESS_TEXT = 'Pipeline run status *SUCCESS* :white_check_mark: : `${BUILD_TXT_JOB_NAME}` *#${BUILD_TXT_BUILD_NUMBER}*'
  static final FAILED_TEXT = 'Pipeline run status *FAILED* :x: : `${BUILD_TXT_JOB_NAME}` *#${BUILD_TXT_BUILD_NUMBER}* \\n:thinking_face: _Pipilene failed on stage_ - *${BUILD_TXT_STAGE_NAME}*'
  static final UNSTABLE_TEXT = 'Pipeline run status *UNSTABLE* :warning: : `${BUILD_TXT_JOB_NAME}` *#${BUILD_TXT_BUILD_NUMBER}*'

  static final ACTION_TEXT = '*Check out the console output* :page_facing_up:'

  static Map<String, String> getTextParams(String jobName, String buildNumber, String stageName){
    [
      'BUILD_TXT_JOB_NAME' : jobName
      , 'BUILD_TXT_BUILD_NUMBER': buildNumber
      , 'BUILD_TXT_STAGE_NAME': stageName
    ]
  }
}
