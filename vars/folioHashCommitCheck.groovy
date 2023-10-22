import org.folio.Constants
import org.folio.utilities.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools

def changeDetected(branch) {

  def awsParameterName = 'Hash-Commit'
  def awsRegion = 'us-west-2'
  def latestCommitHash = getLatestCommitHash(branch)
  def previousSavedHash = getPreviousSavedHashFromSSM(awsRegion,awsParameterName)
  echo "last commit hash ${latestCommitHash}"
  echo "saved hash from ssm ${previousSavedHash}"

  if (latestCommitHash == previousSavedHash) {
    echo "No changes detected. HashDiffDetected :false."
    return false
  } else {
    echo "Changes detected. HashDiffDetected Returning true and updating ssm with new hash: ${latestCommitHash}."
    withUpdateSsmParameterNewValue(awsRegion,awsParameterName,latestCommitHash)
    return true
  }
}

//String getLatestCommitHash(String branch) {
//  String url = "https://api.github.com/repos/folio-org/pipelines-shared-library/branches/RANCHER-999"
//  def response = new HttpClient(this).getRequest(url)
//  if (response.status == HttpURLConnection.HTTP_OK) {
//    return new Tools(this).jsonParse(response.content).commit.sha
//  } else {
//    new Logger(this, 'folioHachCommitCheck').error(new HttpClient(this).buildHttpErrorMessage(response))
//  }
//}


String getLatestCommitHash(String branch) {
  String url = "https://api.github.com/repos/folio-org/pipelines-shared-library/brances/${branch}"
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


void withUpdateSsmParameterNewValue(String region, String parameter_name, String currentHash) {
  sh(script: "aws ssm put-parameter --name ${parameter_name} --region ${region} --value ${currentHash} --type String --overwrite")
}
