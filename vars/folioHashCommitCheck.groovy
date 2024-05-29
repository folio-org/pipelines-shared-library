import org.folio.Constants
import org.folio.utilities.RequestException
import org.folio.utilities.RestClient

// Function to Detect Changes in the platform-complete Repository, Branch Between Job Runs
boolean commitHashChangeDetected(branch) {
  def awsParameterName = 'Hash-Commit'
  def currentCommitHash = getLatestCommitHash('platform-complete', branch)
  def previousSavedHash = getPreviousSavedHashFromSSM(Constants.AWS_REGION, awsParameterName)
  println("Current commit hash: ${currentCommitHash}")
  println("Previous commit hash: ${previousSavedHash}")

  if (currentCommitHash == previousSavedHash) {
    println("Changes not found.")
    return false
  } else {
    println("Changes detected. Updating ssm with new hash: ${currentCommitHash}.")
    awscli.updateSsmParameter(Constants.AWS_REGION, awsParameterName, currentCommitHash)
    return true
  }
}

// Function it returns Lates Commit Hash From the platform-complete repository: snapshot branch
String getLatestCommitHash(String repository, String branch) {
  String url = "${Constants.FOLIO_GITHUB_REPOS_URL}/${repository}/branches/${branch}"
  try {
    def response = new RestClient(this).get(url).body
    if (response?.commit) {
      return response.commit.sha
    }
  } catch (RequestException e) {
    error "An error occurred while fetching GitHub data: ${e.getMessage()}"
  }
}

// Function to get the previous platform-complete $branch commit saved hash from AWS SSM
String getPreviousSavedHashFromSSM(awsRegion, awsParameterName) {
  try {
    return awscli.getSsmParameterValue(awsRegion, awsParameterName)
  } catch (Exception e) {
    error "Error fetching '${awsParameterName}' parameter value from AWS SSM: ${e.message}"
  }
}
