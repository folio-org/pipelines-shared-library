package org.folio.utilities

import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import hudson.util.Secret
import org.folio.Constants

class GitHubClient {

  private static final String GITHUB_TOKEN_CREDENTIAL_ID = "id-jenkins-github-personal-token"

  private Secret gitHubToken

  Logger logger
  RestClient restClient

  GitHubClient(Object context) {
    this.logger = new Logger(context, this.getClass().getCanonicalName())
    this.restClient = new RestClient(context)
    this.gitHubToken = SystemCredentialsProvider.getInstance().getStore()
      .getCredentials(Domain.global()).find { it.getId().equals(GITHUB_TOKEN_CREDENTIAL_ID) }.getSecret()
  }

  String getBranchInfo(String repository, String branch) {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/branches/${branch}"
    Map<String, String> headers = authorizedHeaders()

    return restClient.get(url, headers).body
  }

  Map getCommitInfo(String sha, String repository) {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/commits/${sha}"
    Map<String, String> headers = authorizedHeaders()

    return restClient.get(url, headers).body
  }

  List getFileChangeHistory(String sha, String filePath, String repository) {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/commits?path=${filePath}&sha=${sha}"
    Map<String, String> headers = authorizedHeaders()

    return restClient.get(url, headers).body
  }

  List getTwoCommitsDiff(String previousSha, String currentSha, String repository) {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/compare/${previousSha}...${currentSha}"
    Map<String, String> headers = authorizedHeaders()

    return restClient.get(url, headers).body
  }

  Map getWorkflowRuns(String repository, String runName, String perPage = '30') {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/actions/workflows/${runName}/runs?per_page=${perPage}"
    Map<String, String> headers = authorizedHeaders()
    Map response = [:]

    try {
      response = restClient.get(url, headers).body
    } catch (RequestException e) {
      logger.warning(e.getMessage())
    }

    return response
  }

  Map getWorkflowRunByNumber(String repository, String runName, String runNumber, String perPage = '10') {
    return getWorkflowRuns(repository, runName, perPage)['workflow_runs'].find { it['run_number'] == runNumber.toInteger() }
  }

  Map<String, String> authorizedHeaders() {
    return ['Accept'       : 'application/vnd.github+json',
            'Authorization': "Bearer ${this.gitHubToken}"]
  }
}
