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
    // Debug mode logs the assembled curl command, which carries the bearer token in clear text.
    this.restClient = new RestClient(context)
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

  Map getTwoCommitsDiff(String previousSha, String currentSha, String repository) {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/compare/${previousSha}...${currentSha}"
    Map<String, String> headers = authorizedHeaders()

    try {
      def response = restClient.get(url, headers)
      if (response.responseCode >= 200 && response.responseCode < 300) {
        return response.body instanceof Map ? response.body : [:]
      } else {
        logger.warning("GitHub API returned ${response.responseCode} for commit diff: ${url}")
        return [:]
      }
    } catch (Exception e) {
      logger.warning("Failed to get commit diff for ${repository} ${previousSha}...${currentSha}: ${e.getMessage()}")
      return [:]
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

  Map getWorkflowRunByNumber(String repository, String runName, String runNumber) {
    try {
      def workflowRuns = getWorkflowRuns(repository, runName, '10')
      return workflowRuns['workflow_runs']?.find { it['run_number'] == runNumber.toInteger() }
    } catch (Exception e) {
      logger.warning("Failed to get workflow run by number for ${repository}/${runName}#${runNumber}: ${e.getMessage()}")
      return null
    }
  }

  /**
   * Fetch the raw content of a file at a specific commit SHA.
   * Returns the decoded string, or empty string on failure.
   */
  String getFileContent(String sha, String filePath, String repository) {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/contents/${filePath}?ref=${sha}"
    Map<String, String> headers = authorizedHeaders()

    try {
      def response = restClient.get(url, headers)
      if (response.responseCode >= 200 && response.responseCode < 300) {
        Map body = response.body as Map
        if (body.encoding == 'base64') {
          return new String((body.content as String).replace('\n', '').decodeBase64())
        }
        return body.content?.toString() ?: ''
      } else {
        logger.warning("GitHub API returned ${response.responseCode} for file content: ${url}")
        return ''
      }
    } catch (Exception e) {
      logger.warning("Failed to get file content for ${repository}/${filePath}@${sha}: ${e.getMessage()}")
      return ''
    }
  }

  /**
   * Fetch the open pull request from headBranch into baseBranch.
   * Returns an empty map when none is open, or on any lookup failure.
   */
  Map getOpenPullRequest(String repository, String baseBranch, String headBranch) {
    String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/pulls" +
      "?state=open&base=${baseBranch}&head=${Constants.FOLIO_ORG}:${headBranch}"
    Map<String, String> headers = authorizedHeaders()

    try {
      def response = restClient.get(url, headers)
      if (response.responseCode >= 200 && response.responseCode < 300) {
        return (response.body as List)?.getAt(0) as Map ?: [:]
      } else {
        logger.warning("GitHub API returned ${response.responseCode} for pull request lookup: ${url}")
        return [:]
      }
    } catch (Exception e) {
      logger.warning("Failed to look up pull request ${repository} ${headBranch} -> ${baseBranch}: ${e.getMessage()}")
      return [:]
    }
  }

  /**
   * Add a pull request to the merge queue of its base branch, identified by its GraphQL node id.
   * Returns the merge queue entry, or an empty map when GitHub declined.
   *
   * Enqueueing exists only in the GraphQL API, which answers 200 with an `errors` array instead
   * of an HTTP error code, so the body has to be inspected rather than the status.
   */
  Map enqueuePullRequest(String pullRequestId) {
    String mutation = 'mutation($id: ID!) { enqueuePullRequest(input: {pullRequestId: $id}) ' +
      '{ mergeQueueEntry { id position state } } }'
    Map<String, String> headers = authorizedHeaders()

    try {
      def response = restClient.post(Constants.GITHUB_GRAPHQL_URL,
        [query: mutation, variables: [id: pullRequestId]], headers)
      Map body = response.body as Map ?: [:]

      if (body.errors) {
        logger.warning("GitHub declined to enqueue ${pullRequestId}: ${body.errors}")
        return [:]
      }
      return body.data?.enqueuePullRequest?.mergeQueueEntry as Map ?: [:]
    } catch (Exception e) {
      logger.warning("Failed to enqueue ${pullRequestId}: ${e.getMessage()}")
      return [:]
    }
  }

  Map<String, String> authorizedHeaders() {
    return ['Accept'       : 'application/vnd.github+json',
            'Authorization': "Bearer ${this.gitHubToken}"]
  }
}
