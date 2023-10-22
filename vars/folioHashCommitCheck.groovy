import org.folio.Constants
import org.folio.utilities.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools

def changeDetected(branch) {

  def parameter_name = 'Hash-Commit'
  def region = 'us-east-2'
  def currentHash = getCurrentGitHash(branch)
  def savedHash = getSavedHashFromSSM(region,parameter_name)

  if (currentHash == savedHash) {
    echo "No changes detected. Returning true."
    return true
  } else {
    echo "Changes detected. Returning false and updating ssm with new hash: ${currentHash}."
    UpdateSsmParameterValue(region,parameter_name,currentHash)
    return false
  }
}

def getCurrentGitHash(branch) {
  try {
    return sh(script: "git ls-remote https://github.com/folio-org/pipelines-shared-library.git refs/heads/${branch} | cut -f 1", returnStdout: true).trim()
  } catch (Exception e) {
    error "Error fetching Git hash: ${e.message}"
    return null
  }
}

def getSavedHashFromSSM(region,parameter_name) {
    def parameterValue = GetSsmParameterValue(region,parameter_name);
    return parameterValue
}


def GetSsmParameterValue(String region, String parameter_name) {
  return sh(script: "aws ssm get-parameter --name ${parameter_name} --region ${region} --query  \"Parameter.Value\" --output text", returnStatus: true)
}

void UpdateSsmParameterValue(String region, String parameter_name, String currentHash) {
  sh(script: "aws ssm put-parameter --name ${parameter_name} --region ${region} --value value --type ${currentHash} --overwrite")
}
