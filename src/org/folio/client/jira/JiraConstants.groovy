package org.folio.client.jira

import org.folio.client.jira.model.JiraResources

class JiraConstants {

  static final String URL = 'https://folio-org.atlassian.net'
  static final String API_URL_PART = "/rest/api/2/"
  static final String API_URL = "${JiraConstants.URL}${API_URL_PART}"

  static final String ISSUE_URL = "${API_URL}${JiraResources.ISSUE}/"
  static final String ISSUES_VIEW_URL = "${URL}/issues"
  static final String FILTERED_ISSUES_VIEW_URL = "${ISSUES_VIEW_URL}/?jql="

  static final String CREDENTIALS_ID = 'jenkins-jira'
}
