import org.folio.Constants
import org.folio.utilities.GitHubClient

String getCurrentBuildSha(String branch) {
  Map branchInfo = new GitHubClient(this).getBranchInfo('platform-complete', branch)

  return branchInfo.commit.sha
}

@SuppressWarnings('GrMethodMayBeStatic')
String getPreviousBuildSha() {
  String awsSsmParameterName = 'Hash-Commit'
  String awsRegion = Constants.AWS_REGION
  awscli.withAwsClient {
    return awscli.getSsmParameterValue(awsRegion, awsSsmParameterName)
  }
}

@SuppressWarnings('GrMethodMayBeStatic')
void updateBuildSha(String sha) {
  String awsSsmParameterName = 'Hash-Commit'
  String awsRegion = Constants.AWS_REGION
  awscli.withAwsClient {
    awscli.updateSsmParameter(awsRegion, awsSsmParameterName, sha)
  }
}

boolean isInstallJsonChanged(String previousSha, String currentSha) {
  println("Current build sha: ${currentSha}\nPrevious build sha: ${previousSha}")
  if (previousSha == currentSha) {
    return false
  } else {
    Map commitsDiff = new GitHubClient(this).getTwoCommitsDiff(previousSha, currentSha, 'platform-complete')

    println("Changed files: ${commitsDiff.files*.filename}")

    return commitsDiff.files.any { it.filename == 'install.json' }
  }
}

/**
 * Checks if the latest commit SHA for a given GitHub repo/branch has changed compared to the value stored in SSM.
 * If changed, updates the SSM parameter and returns true. Otherwise, returns false.
 */
def hasRepoShaChanged(String org, String repo, String branch = 'master', String ssmParameterName) {
    String repoUrl = "https://github.com/${org}/${repo}.git"
    String awsRegion = Constants.AWS_REGION

    String latestSha = sh(script: "git ls-remote ${repoUrl} refs/heads/${branch} | cut -f1", returnStdout: true).trim()

    String previousSha = null
    awscli.withAwsClient {
        previousSha = awscli.getSsmParameterValue(awsRegion, ssmParameterName)
    }

    if (previousSha == latestSha) {
        return false
    } else {
        awscli.withAwsClient {
            awscli.updateSsmParameter(awsRegion, ssmParameterName, latestSha)
        }
        return true
    }
}

def hasFolioIntegrationTestsShaChanged(String branch = 'master', String ssmParameterName = 'FOLIO_INTEGRATION_TESTS_SHA') {
    return hasRepoShaChanged('folio-org', 'folio-integration-tests', branch, ssmParameterName)
}

def hasStripesTestingShaChanged(String branch = 'master', String ssmParameterName = 'STRIPES_TESTING_SHA') {
    return hasRepoShaChanged('folio-org', 'stripes-testing', branch, ssmParameterName)
}
