package org.folio.client.reportportal

class ReportPortalConstants {

  public static final String URL = "https://report-portal.ci.folio.org"
  public static final String API_URL = "${ReportPortalConstants.URL}/api/v1"
  public static final String KARATE_CRED_TEMPLATE_FILE_PATH = "testrail-integration/src/main/resources/reportportal.properties"

  public static final String KARATE_PROJECT_NAME = "junit5-integration"
  public static final String CYPRESS_PROJECT_NAME = "cypress-nightly"

  public static final String CREDENTIALS_ID = "report-portal-api-key-1"

  public static final String KARATE_EXEC_PARAM_TEMPLATE = '-Drp.launch.uuid="${launch_id}"'
  public static final String CYPRESS_EXEC_PARAM_TEMPLATE = '''--reporter "@reportportal/agent-js-cypress" \
   --reporter-options \
   endpoint="${api_url}",apiKey="${api_key}",project="${project_name}",description="${description}",launch="${launch_name}",launchId="${launch_id}",mode="DEFAULT",attributes="${attributes}"
'''
}
