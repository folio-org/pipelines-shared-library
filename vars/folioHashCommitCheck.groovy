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
