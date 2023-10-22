import org.folio.Constants
import org.folio.utilities.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools

def changeDetected(branch) {

  def parameter_name = 'Hash-Commit'
  def region = 'us-west-2'
  def currentHash = getLastCommitHash(branch)
  def savedHash = getSavedHashFromSSM(region,parameter_name)
  echo "last commit hash ${currentHash}"
  echo "saved hash from ssm ${savedHash}"

  if (currentHash == savedHash) {
    echo "No changes detected. Returning true."
    return false
  } else {
    echo "Changes detected. Returning true and updating ssm with new hash: ${currentHash}."
    UpdateSsmParameterValue(region,parameter_name,currentHash)
    return true
  }
}

//def getCurrentGitHash(branch) {
//  try {
//    //return sh(script: "git ls-remote https://github.com/folio-org/pipelines-shared-library.git refs/heads/${branch} | cut -f 1", returnStdout: true).trim()
//    def parameterValue1 = sh(script: 'aws ssm get-parameter --name Hash-Commit --region us-west-2 --query "Parameter.Value" --output text', returnStdout: true).trim()
//    echo "Value of Last Hash-Commit: ${parameterValue1}"
//  } catch (Exception e) {
//    error "Error fetching Git hash: ${e.message}"
//  }
//}


String getLastCommitHash(String branch) {
  String url = "https://api.github.com/repos/folio-org/pipelines-shared-library/branches/RANCHER-999"
  def response = new HttpClient(this).getRequest(url)
  if (response.status == HttpURLConnection.HTTP_OK) {
    return new Tools(this).jsonParse(response.content).commit.sha
  } else {
    new Logger(this, 'folioHachCommitCheck').error(new HttpClient(this).buildHttpErrorMessage(response))
  }
}

String getSavedHashFromSSM(region, parameter_name) {
  try {
    def parameterValue = sh(
      script: "aws ssm get-parameter --name ${parameter_name} --region ${region} --query 'Parameter.Value' --output text",
      returnStdout: true
    ).trim()
    echo "Value of Previous Saved Hash-Commit: ${parameterValue}"
    return parameterValue
  } catch (Exception e) {
    error "Error fetching parameter value from AWS SSM: ${e.message}"
    return null
  }
}


void UpdateSsmParameterValue(String region, String parameter_name, String currentHash) {
  sh(script: "aws ssm put-parameter --name ${parameter_name} --region ${region} --value ${currentHash} --type String --overwrite")
}
