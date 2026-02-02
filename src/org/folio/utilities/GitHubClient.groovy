package org.folio.utilities

import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import hudson.util.Secret
import org.folio.Constants

class GitHubClient {

  private static final String GITHUB_TOKEN_CREDENTIAL_ID = "github-jenkins-service-user-token"

  private Secret gitHubToken

  Logger logger
  RestClient restClient

  GitHubClient(Object context) {
    this.logger = new Logger(context, this.getClass().getCanonicalName())
    this.restClient = new RestClient(context, true)
    this.gitHubToken = SystemCredentialsProvider.getInstance().getStore()
      .getCredentials(Domain.global()).find { it.getId().equals(GITHUB_TOKEN_CREDENTIAL_ID) }.getSecret()
  }

  Map getBranchInfo(String repository, String branch) {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/branches/${branch}"
    Map<String, String> headers = authorizedHeaders()

    try {
      def response = restClient.get(url, headers)
      if (response.responseCode >= 200 && response.responseCode < 300) {
        return response.body as Map ?: [:]
      } else {
        logger.warning("GitHub API returned ${response.responseCode} for branch info: ${url}")
        return [:]
      }
    } catch (Exception e) {
      logger.warning("Failed to get branch info for ${repository}/${branch}: ${e.getMessage()}")
      return [:]
    }
  }

  Map getCommitInfo(String sha, String repository) {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/commits/${sha}"
    Map<String, String> headers = authorizedHeaders()

    try {
      def response = restClient.get(url, headers)
      if (response.responseCode >= 200 && response.responseCode < 300) {
        return response.body
      } else {
        throw new RuntimeException("GitHub API returned ${response.responseCode} for ${url}")
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to get commit info for ${repository}/${sha}: ${e.getMessage()}", e)
    }
  }

  List getFileChangeHistory(String sha, String filePath, String repository) {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/commits?path=${filePath}&sha=${sha}"
    Map<String, String> headers = authorizedHeaders()

    try {
      def response = restClient.get(url, headers)
      if (response.responseCode >= 200 && response.responseCode < 300) {
        return response.body ?: []
      } else {
        logger.warning("GitHub API returned ${response.responseCode} for file change history: ${url}")
        return []
      }
    } catch (Exception e) {
      logger.warning("Failed to get file change history for ${repository}/${filePath}: ${e.getMessage()}")
      return []
    }
  }

  List getTwoCommitsDiff(String previousSha, String currentSha, String repository) {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/compare/${previousSha}...${currentSha}"
    Map<String, String> headers = authorizedHeaders()

    try {
      def response = restClient.get(url, headers)
      if (response.responseCode >= 200 && response.responseCode < 300) {
        return response.body ?: []
      } else {
        logger.warning("GitHub API returned ${response.responseCode} for commit diff: ${url}")
        return []
      }
    } catch (Exception e) {
      logger.warning("Failed to get commit diff for ${repository} ${previousSha}...${currentSha}: ${e.getMessage()}")
      return []
    }
  }

  Map getWorkflowRuns(String repository, String runName, String perPage = '30') {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/actions/workflows/${runName}/runs?per_page=${perPage}"
    Map<String, String> headers = authorizedHeaders()

    try {
      def response = restClient.get(url, headers)
      if (response.responseCode >= 200 && response.responseCode < 300) {
        return response.body ?: [workflow_runs: []]
      } else {
        logger.warning("GitHub API returned ${response.responseCode} for workflow runs: ${url}")
        return [workflow_runs: []]
      }
    } catch (Exception e) {
      logger.warning("Failed to get workflow runs for ${repository}/${runName}: ${e.getMessage()}")
      return [workflow_runs: []]
    }
  }

  Map getWorkflowRunByNumber(String repository, String runName, String runNumber, String perPage = '10') {
    try {
      def workflowRuns = getWorkflowRuns(repository, runName, perPage)
      return workflowRuns['workflow_runs']?.find { it['run_number'] == runNumber.toInteger() }
    } catch (Exception e) {
      logger.warning("Failed to get workflow run by number for ${repository}/${runName}#${runNumber}: ${e.getMessage()}")
      return null
    }
  }

  Map<String, String> authorizedHeaders() {
    return ['Accept'       : 'application/vnd.github+json',
            'Authorization': "Bearer ${this.gitHubToken}"]
  }
}
