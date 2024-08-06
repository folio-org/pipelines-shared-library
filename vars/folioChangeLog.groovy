import hudson.model.Action
import hudson.model.Job
import hudson.model.Run
import hudson.plugins.git.util.BuildData
import jenkins.model.Jenkins
import org.folio.utilities.Logger
import org.folio.utilities.RestClient

List<Map<String, String>> call() {}

Map<String, String> _getJenkinsBuildChangelog(String moduleName, int moduleBuildId) {
  Logger log = new Logger(this, 'getJenkinsBuildChangelog')

  Job moduleJob = Jenkins.instance.getItemByFullName("/folio-org/${moduleName}/master")
  if (moduleJob == null) {
    log.warning("Job not found for module: ${moduleName}")
    return null
  }

  Run moduleBuild = moduleJob.getBuildByNumber(moduleBuildId)
  if (moduleBuild == null) {
    log.warning("Build not found for module: ${moduleName} with Build ID: ${moduleBuildId}")
    return null
  }

  Action moduleBuildAction = moduleBuild.getActions(BuildData).find { moduleBuildAction ->
    moduleBuildAction.getRemoteUrls()[0] == "https://github.com/folio-org/${moduleName}.git"
  }
  if (moduleBuildAction == null) {
    log.warning("Build data not found for module: ${moduleName}")
    return null
  }

  String commitHash = moduleBuildAction.getLastBuiltRevision().sha1.name
  if (commitHash == null) {
    log.warning("Commit hash not found for module: ${moduleName}")
    return null
  }

  String getCommitUrl = "https://api.github.com/repos/folio-org/${moduleName}/commits/${commitHash}"
  Map<String, String> headers = ['Accept': 'application/vnd.github+json']
  withCredentials([string(credentialsId: 'id-jenkins-github-personal-token', variable: 'token')]) {
    headers.put('Authorization', "Bearer \$token")
  }
  Map response
  try {
    response = new RestClient(this).get(getCommitUrl, headers).body
  } catch (Exception e) {
    log.warning("Failed to fetch commit data from GitHub for ${moduleName}: ${e.message}")
    return null
  }

  return [
    hash  : commitHash,
    commit: response.commit?.message ?: "No commit message",
    author: response.commit?.author?.name ?: "Unknown"
  ]
}

Map<String, String> _getGitHubWorkflowChangelog() {}





