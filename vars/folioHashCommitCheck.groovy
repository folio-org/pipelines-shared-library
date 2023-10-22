import org.folio.Constants
import org.folio.utilities.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools


// Function to Detect Changes in the platform-complete Repository, Branch Between Job Runs
def commitHashChangeDetected(branch) {

  def awsParameterName = 'Hash-Commit'
  def awsRegion = 'us-west-2'
  def latestCommitHash = getLatestCommitHash(branch)
  def previousSavedHash = getPreviousSavedHashFromSSM(awsRegion,awsParameterName)
  echo "latestCommitHash ${latestCommitHash}"
  echo "previousSavedHash ${previousSavedHash}"

  if (latestCommitHash == previousSavedHash) {
    echo "No changes detected. HashDiffDetected :false."
    return false
  } else {
    echo "Changes detected. HashDiffDetected Returning true and updating ssm with new hash: ${latestCommitHash}."
    withUpdateSsmParameterNewValue(awsRegion,awsParameterName,latestCommitHash)
    return true
  }
}

// Function it returns Lates Commit Hash From the platform-complete repository: snapshot branch
String getLatestCommitHash(String branch) {
  String url = "https://api.github.com/repos/folio-org/pipelines-shared-library/branches/${branch}"
  try {
    def response = new HttpClient(this).getRequest(url)

    if (response.status == HttpURLConnection.HTTP_OK) {
      def parsedResponse = new Tools(this).jsonParse(response.content)
      if (parsedResponse && parsedResponse.commit && parsedResponse.commit.sha) {
        return parsedResponse.commit.sha
      } else {
        error "Failed to parse GitHub response or retrieve commit SHA."
        return null
      }
    } else {
      error "GitHub API request failed with status code ${response.status}: ${response.body}"
      return null
    }
  } catch (Exception e) {
    error "An error occurred while fetching GitHub data: ${e.message}"
    return null
  }
}

// Function to get the previous platforme-complete $branch commit saved hash from AWS SSM
String getPreviousSavedHashFromSSM(awsRegion, awsParameterName) {
  try {
    def parameterValue = sh(
      script: "aws ssm get-parameter --name ${awsParameterName} --region ${awsRegion} --query 'Parameter.Value' --output text",
      returnStdout: true
    ).trim()
    echo "Value of Previous Saved Hash-Commit: ${parameterValue}"
    return parameterValue
  } catch (Exception e) {
    error "Error fetching parameter value from AWS SSM: ${e.message}"
    return null
  }
}


// Function to update the SSM parameter with a new hash value
void withUpdateSsmParameterNewValue(String awsRegion, String awsParameterName, String latestCommitHash) {
  sh(script: "aws ssm put-parameter --name ${awsParameterName} --region ${awsRegion} --value ${latestCommitHash} --type String --overwrite")
}
