package org.folio.slack.templates

class SlackTeamTestResultTemplates {

  private SlackTeamTestResultTemplates(){}

  static final SUCCESS_TEXT = 'All modules for ${TEAM_TEST_TXT_TEAM_NAME} team have successful result'
  static final FAILED_TEXT = '${TEAM_TEST_TXT_FAILED_CNT} of ${TEAM_TEST_TXT_TOTAL_CNT} modules were failed for ${TEAM_TEST_TXT_TEAM_NAME} team'

  static final EXISTING_ISSUES_ACTION_TEXT = '*Check out the existing issues* :information_source: '
  static final CREATED_ISSUES_ACTION_TEXT = '*Check out the created issues* :information_source: '

  static final FAILED_MODULE_FIELD_TITLE = ':gear: ${TEAM_TEST_FIELD_MODULE_NAME}'
  static final FAILED_MODULE_FIELD_VALUE = 'Has ${TEAM_TEST_FIELD_FAILED_CNT} failures of ${TEST_FIELD_TOTAL_CNT} total tests'

  static Map<String, String> getTextParams(String teamName, String failedCount, String totalCount){
    [
      'TEAM_TEST_TXT_TEAM_NAME' : teamName
      , 'TEAM_TEST_TXT_FAILED_CNT': failedCount
      , 'TEAM_TEST_TXT_TOTAL_CNT': totalCount
    ]
  }

  static Map<String, String> getFieldParams(String moduleName, String failedCount, String totalCount){
    [
      'TEST_FIELD_MODULE_NAME' : moduleName
      , 'TEAM_TEST_FIELD_FAILED_CNT' : failedCount
      , 'TEST_FIELD_TOTAL_CNT' : totalCount
    ]
  }
}
