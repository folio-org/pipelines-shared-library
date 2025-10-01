import com.cloudbees.groovy.cps.NonCPS
import hudson.model.Action
import hudson.model.Job
import hudson.model.Run
import hudson.plugins.git.util.BuildData
import jenkins.model.Jenkins
import org.folio.models.ChangelogEntry
import org.folio.models.module.FolioModule
import org.folio.models.module.ModuleType
import org.folio.slack.SlackHelper
import org.folio.utilities.GitHubClient
import org.folio.utilities.Logger

import java.util.regex.Matcher

List<ChangelogEntry> call(String previousSha, String currentSha) {

  GitHubClient gitHubClient = new GitHubClient(this)
  String platformCompleteRepositoryName = 'platform-complete'

  List allChangeLogShas = gitHubClient.getTwoCommitsDiff(previousSha, currentSha, platformCompleteRepositoryName)['commits']
    .collect { it['sha'] }

  List allInstallJsonChangeLogShas = gitHubClient.getFileChangeHistory(currentSha, 'install.json', platformCompleteRepositoryName)
    .collect { it['sha'] }

  List allEurekaPlatformJsonChangeLogShas = gitHubClient.getFileChangeHistory(currentSha, 'eureka-platform.json', platformCompleteRepositoryName)
    .collect { it['sha'] }

  List installJsonChangeLogShas = allChangeLogShas.intersect(allInstallJsonChangeLogShas)
  List eurekaPlatformJsonChangeLogShas = allChangeLogShas.intersect(allEurekaPlatformJsonChangeLogShas)


  List updatedModulesList = []
  
  echo "Processing install.json changes: ${installJsonChangeLogShas.size()} commits"
  installJsonChangeLogShas.each { sha ->
    def installModules = getUpdatedModulesList(gitHubClient.getCommitInfo(sha, platformCompleteRepositoryName), 'install.json')
    echo "Found ${installModules.size()} install.json modules in commit ${sha}: ${installModules}"
    updatedModulesList.addAll(installModules)
  }
  
  echo "Processing eureka-platform.json changes: ${eurekaPlatformJsonChangeLogShas.size()} commits"
  eurekaPlatformJsonChangeLogShas.each { sha ->
    def eurekaModules = getUpdatedModulesList(gitHubClient.getCommitInfo(sha, platformCompleteRepositoryName), 'eureka-platform.json')
    echo "Found ${eurekaModules.size()} eureka-platform.json modules in commit ${sha}: ${eurekaModules}"
    updatedModulesList.addAll(eurekaModules)
  }
  
  echo "Total modules found: ${updatedModulesList.size()}"
  echo "All modules: ${updatedModulesList}"

  List<FolioModule> updatedModulesObjectsList = []
  updatedModulesList.each { id ->
    FolioModule module = new FolioModule()
    module.loadModuleDetails(id)

    updatedModulesObjectsList << module
  }

  List<ChangelogEntry> changeLogEntriesList = []
  updatedModulesObjectsList.each { module ->
    ChangelogEntry changeLogEntry = new ChangelogEntry()
    String repositoryName = getRepositoryName(module)
    changeLogEntry.module = module

    echo "Processing module: ${module.name} (${module.type}) -> Repository: ${repositoryName}"

    try {
      // First try GitHub workflows (for modules that use GitHub Actions for builds)
      changeLogEntry.sha = getGitHubWorkflowSha(repositoryName, module.buildId.toInteger(), module.type)
      
      if (changeLogEntry.sha && changeLogEntry.sha != 'Unknown') {
        echo "Found SHA from GitHub workflows: ${changeLogEntry.sha} for ${repositoryName} build #${module.buildId}"
      } else {
        // If GitHub workflows don't have builds, try external Jenkins (for Jenkins-built modules)
        echo "No GitHub workflow build found, trying external Jenkins for ${repositoryName} build #${module.buildId}"
        changeLogEntry.sha = getExternalJenkinsBuildSha(repositoryName, module.buildId.toInteger())
        
        if (changeLogEntry.sha && changeLogEntry.sha != 'Unknown') {
          echo "Found SHA from external Jenkins: ${changeLogEntry.sha} for ${repositoryName} build #${module.buildId}"
        } else {
          echo "No build found in GitHub workflows or external Jenkins for ${repositoryName} build #${module.buildId}"
        }
      }
      
      if (changeLogEntry.sha && changeLogEntry.sha != 'Unknown') {
        // Get commit info from GitHub
        Map commitInfo = gitHubClient.getCommitInfo(changeLogEntry.sha, repositoryName)
        changeLogEntry.author = commitInfo?.commit?.author?.name ?: 'Unknown author'
        changeLogEntry.commitMessage = commitInfo?.commit?.message?.split('\n', 2)?.getAt(0) ?: "Build ${module.buildId} (SHA: ${changeLogEntry.sha.take(7)})"
        changeLogEntry.commitLink = commitInfo?.html_url ?: null
        
        echo "Commit: ${changeLogEntry.commitMessage} by ${changeLogEntry.author}"
      } else {
        echo "Warning: Could not find build for ${repositoryName} build #${module.buildId}"
        changeLogEntry.sha = 'Unknown'
        changeLogEntry.author = 'Unknown author'
        changeLogEntry.commitMessage = "Unable to find build ${module.buildId} for ${repositoryName} - checked GitHub workflows and external Jenkins"
        changeLogEntry.commitLink = null
      }
    } catch (Exception e) {
      echo "Error processing module ${repositoryName}: ${e.getMessage()}"
      changeLogEntry.sha = 'Unknown'
      changeLogEntry.author = 'Unknown author' 
      changeLogEntry.commitMessage = "Error retrieving build ${module.buildId} for ${repositoryName}: ${e.getMessage()}"
      changeLogEntry.commitLink = null
    }

    changeLogEntriesList << changeLogEntry
  }

  return changeLogEntriesList
}

String getRepositoryName(FolioModule module) {
  switch (module.type) {
    case ModuleType.FRONTEND:
      // folio_inventory -> ui-inventory
      return module.name.replace('folio_', 'ui-')
    
    case ModuleType.BACKEND:
    case ModuleType.EDGE:
    case ModuleType.MGR:
      // mod-*, edge-*, mgr-* stay as is
      return module.name
    
    case ModuleType.SIDECAR:
      // folio-module-sidecar
      return 'folio-module-sidecar'
    
    case ModuleType.KONG:
      // folio-kong
      return 'folio-kong'
    
    case ModuleType.KEYCLOAK:
      // Check if it's a mod-*-keycloak or folio-keycloak
      if (module.name.startsWith('mod-') && module.name.endsWith('-keycloak')) {
        return module.name // mod-consortia-keycloak, mod-login-keycloak, etc.
      } else {
        return 'folio-keycloak' // folio-keycloak
      }
    
    default:
      echo "Warning: Unknown module type ${module.type} for module ${module.name}"
      return module.name
  }
}

String getGitHubWorkflowSha(String repositoryName, int buildId, ModuleType moduleType) {
  if (!buildId || buildId <= 0) {
    echo "Invalid build ID: ${buildId} for repository: ${repositoryName}"
    return 'Unknown'
  }
  
  echo "Searching for GitHub workflow run #${buildId} in repository ${repositoryName}"
  
  // Define workflow names based on module type
  def workflowNames = []
  switch (moduleType) {
    case ModuleType.FRONTEND:
      workflowNames = ['ui.yml', 'build-npm.yml', 'build.yml', 'ci.yml', 'node.yml']
      break
    case ModuleType.BACKEND:
    case ModuleType.EDGE:
    case ModuleType.MGR:
      workflowNames = ['build.yml', 'ci.yml', 'maven.yml', 'java.yml', 'build-snapshot.yml']
      break
    case ModuleType.OKAPI:
      // Okapi has specific workflow files
      workflowNames = ['postgres.yml', 'api-doc.yml', 'api-lint.yml', 'api-schema-lint.yml', 'build.yml', 'ci.yml']
      break
    case ModuleType.SIDECAR:
      workflowNames = ['build.yml', 'ci.yml', 'main.yml']
      break
    case ModuleType.KONG:
      workflowNames = ['do-docker.yml', 'test.yml', 'build.yml', 'ci.yml']
      break
    case ModuleType.KEYCLOAK:
      if (repositoryName == 'folio-keycloak') {
        workflowNames = ['do-docker.yml', 'build.yml', 'ci.yml']
      } else {
        // For mod-*-keycloak modules (including consortia-related ones)
        workflowNames = ['build.yml', 'ci.yml', 'maven.yml', 'do-docker.yml']
      }
      break
    default:
      // More comprehensive default patterns for unknown/Eureka modules
      workflowNames = ['build.yml', 'ci.yml', 'main.yml', 'maven.yml', 'ui.yml', 'do-docker.yml']
  }
  
  echo "Trying workflow files: ${workflowNames}"
  
  for (String workflowName : workflowNames) {
    try {
      echo "Checking workflow file: ${workflowName} for run #${buildId}"
      
      // First, let's check if the workflow exists by getting some runs
      echo "Getting workflow runs for ${repositoryName}/${workflowName} to check if workflow exists"
      def workflowRunsCheck = gitHubClient.getWorkflowRuns(repositoryName, workflowName, '5')
      echo "Workflow runs check result: ${workflowRunsCheck}"
      
      def workflowRun = gitHubClient.getWorkflowRunByNumber(repositoryName, workflowName, buildId.toString())
      if (workflowRun?.head_sha) {
        echo "Found workflow run #${buildId} in ${workflowName}: SHA ${workflowRun.head_sha} (run_id: ${workflowRun.id})"
        return workflowRun.head_sha
      } else if (workflowRun == null) {
        echo "No workflow run #${buildId} found in ${workflowName}"
      } else {
        echo "Workflow run #${buildId} found in ${workflowName} but missing head_sha: ${workflowRun}"
      }
    } catch (Exception e) {
      echo "Workflow ${workflowName} check failed: ${e.getMessage()}"
      echo "Exception details: ${e.getClass().getName()}: ${e.getMessage()}"
    }
  }
  
  echo "No workflow run found for build #${buildId} in any workflow files: ${workflowNames}"
  return 'Unknown'
}

String getExternalJenkinsBuildSha(String moduleName, int moduleBuildId) {
  if (!moduleBuildId || moduleBuildId <= 0) {
    echo "Invalid module build ID: ${moduleBuildId} for module: ${moduleName}"
    return 'Unknown'
  }
  
  try {
    String jenkinsBaseUrl = "https://jenkins-aws.indexdata.com"
    String buildApiUrl = "${jenkinsBaseUrl}/job/folio-org/job/${moduleName}/job/master/${moduleBuildId}/api/json"
    
    echo "Checking external Jenkins: ${buildApiUrl}"
    
    def response = sh(
      script: "curl -s -f '${buildApiUrl}'",
      returnStdout: true
    ).trim()
    
    if (!response) {
      echo "Empty response from external Jenkins API for ${moduleName} build #${moduleBuildId}"
      return 'Unknown'
    }
    
    def buildInfo = readJSON text: response
    
    // Look for Git-related actions in the build
    def gitAction = buildInfo.actions?.find { action ->
      action._class?.contains('BuildData') || action.lastBuiltRevision?.SHA1
    }
    
    if (gitAction?.lastBuiltRevision?.SHA1) {
      String sha = gitAction.lastBuiltRevision.SHA1
      
      // Check the Git repository URL to avoid jenkins-pipeline-libs pollution
      String gitUrl = 'Unknown'
      if (gitAction.remoteUrls) {
        gitUrl = gitAction.remoteUrls[0] ?: 'Unknown'
      }
      
      echo "External Jenkins SHA: ${sha}, Git URL: ${gitUrl}"
      
      // Reject if it's from jenkins-pipeline-libs (the source of our previous issues)
      if (gitUrl.contains('jenkins-pipeline-libs')) {
        echo "Rejecting SHA from jenkins-pipeline-libs to avoid incorrect commit info"
        return 'Unknown'
      }
      
      // Validate that the Git URL matches the expected module repository
      String expectedUrl = "https://github.com/folio-org/${moduleName}.git"
      if (gitUrl == expectedUrl || gitUrl == 'Unknown') {
        echo "Successfully retrieved SHA ${sha} from external Jenkins for ${moduleName}"
        return sha
      } else {
        echo "Git URL mismatch - Expected: ${expectedUrl}, Found: ${gitUrl}"
        return 'Unknown'
      }
    } else {
      echo "No Git SHA found in external Jenkins build data for ${moduleName} build #${moduleBuildId}"
      return 'Unknown'
    }
    
  } catch (Exception e) {
    echo "Exception getting build SHA from external Jenkins for ${moduleName} build #${moduleBuildId}: ${e.getMessage()}"
    return 'Unknown'
  }
}

@SuppressWarnings('GrMethodMayBeStatic')

static List getUpdatedModulesList(Map commitInfo, String filename = 'install.json') {
  try {
    String pattern = /(?m)-\s+"id" : "(.*?)",\n\+\s+"id" : "(.*?)",/
    def fileInfo = commitInfo['files']?.find { it['filename'] == filename }
    
    if (!fileInfo || !fileInfo['patch']) {
      return []
    }
    
    Matcher matches = fileInfo['patch'] =~ pattern
    return matches.collect { match -> match[2] }
  } catch (Exception e) {
    echo "Error parsing ${filename} changes: ${e.getMessage()}"
    return []
  }
}

@SuppressWarnings('GrMethodMayBeStatic')
List renderChangelogBlock(List<ChangelogEntry> changeLogEntriesList) {
  List blocks = [[type: "divider"],
                 [type: "header",
                  text: [type : "plain_text",
                         text : ":scroll:Changelog:scroll:",
                         emoji: true]]]

  String changeLog = ''
  List<ChangelogEntry> sortedChangeLogEntriesList = getSortedChangeLogEntriesList(changeLogEntriesList)

  for (entry in sortedChangeLogEntriesList) {
    String elementHeader = "*${entry.module.id}*"
    String elementCommit = "`${entry.sha.take(7)}`"
    String elementMessage = entry.commitLink ? "<${entry.commitLink}|${entry.commitMessage}>" : entry.commitMessage
    String elementAuthor = entry.author ? "by ${entry.author}" : ''
    String element = ">${elementHeader}\\n>${elementCommit} ${elementMessage} ${elementAuthor}\\n\\n"

    // Slack text section is limited by number of characters (3001)
    if ((changeLog + element).length() > 2998) {
      changeLog += '...'
      break
    }

    changeLog += element
  }

  blocks << [type: "section",
             text: [type: "mrkdwn",
                    text: changeLog.replace('\\n', '\n')]]

  return blocks
}

@SuppressWarnings('GrMethodMayBeStatic')
String renderChangelogSection(List<ChangelogEntry> changeLogEntriesList) {
  String changeLog = ''
  List<ChangelogEntry> sortedChangeLogEntriesList = getSortedChangeLogEntriesList(changeLogEntriesList)

  for (entry in sortedChangeLogEntriesList) {
    String elementHeader = "*${entry.module.id}*"
    String elementCommit = "`${entry.sha.take(7)}`"
    String elementMessage = entry.commitLink ? "<${entry.commitLink}|${entry.commitMessage}>" : entry.commitMessage
    String elementAuthor = entry.author ? "by ${entry.author}" : ''
    String element = "${elementHeader}\\n${elementCommit} ${elementMessage} ${elementAuthor}\\n\\n"

    // Slack text section is limited by number of characters (3001)
    if ((changeLog + element).length() > 2998) {
      changeLog += '>...'
      break
    }

    changeLog += element
  }

  String section = SlackHelper.renderSection(':scroll:Changelog:scroll:', changeLog.replace('"', '\\"'), '#808080', [], [])

  return section
}

String getPlainText(List<ChangelogEntry> changeLogEntriesList){
  StringBuilder plainTextBuilder = new StringBuilder()

  changeLogEntriesList.each { entry ->
    String moduleId = entry.module.id
    String sha = entry.sha.take(7)
    String commitMessage = entry.commitMessage
    String author = entry?.author ?: "Unknown author"

    plainTextBuilder.append("${moduleId} ${commitMessage} by ${author} (${sha})\n")
  }

  return plainTextBuilder.toString()
}

@NonCPS
static List<ChangelogEntry> getSortedChangeLogEntriesList(List<ChangelogEntry> toSort) {
  toSort.sort() { a, b -> a.module.id <=> b.module.id }
}
