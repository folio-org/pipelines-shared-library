import org.folio.utilities.GitHubClient

/**
 * Provides authenticated GitHub API client access using Jenkins credentials.
 * Wraps GitHubClient to ensure credentials are properly loaded via withCredentials.
 *
 * Usage:
 *   def branchInfo = githubApiClient { client ->
 *     client.getBranchInfo('platform-lsp', 'master')
 *   }
 */
def call(Closure body) {
  withCredentials([string(credentialsId: 'github-jenkins-service-user-token', variable: 'GITHUB_TOKEN')]) {
    GitHubClient client = new GitHubClient(this, env.GITHUB_TOKEN)
    return body(client)
  }
}
