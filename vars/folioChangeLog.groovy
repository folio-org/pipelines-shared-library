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
      // Get SHA from GitHub workflows only - no Jenkins dependency
      changeLogEntry.sha = getGitHubWorkflowSha(repositoryName, module.buildId.toInteger(), module.type)
      
      if (changeLogEntry.sha && changeLogEntry.sha != 'Unknown') {
        echo "Successfully found SHA ${changeLogEntry.sha} for ${repositoryName} build #${module.buildId}"
        
        // Get commit info from GitHub
        Map commitInfo = gitHubClient.getCommitInfo(changeLogEntry.sha, repositoryName)
        changeLogEntry.author = commitInfo?.commit?.author?.name ?: 'Unknown author'
        changeLogEntry.commitMessage = commitInfo?.commit?.message?.split('\n', 2)?.getAt(0) ?: "Build ${module.buildId} (SHA: ${changeLogEntry.sha.take(7)})"
        changeLogEntry.commitLink = commitInfo?.html_url ?: null
        
        echo "Commit: ${changeLogEntry.commitMessage} by ${changeLogEntry.author}"
      } else {
        echo "Warning: Could not find workflow run for ${repositoryName} build #${module.buildId}"
        changeLogEntry.sha = 'Unknown'
        changeLogEntry.author = 'Unknown author'
        changeLogEntry.commitMessage = "Unable to find GitHub workflow run ${module.buildId} for ${repositoryName}"
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
    case ModuleType.SIDECAR:
      // folio-module-sidecar doesn't seem to have GitHub workflows
      workflowNames = ['build.yml', 'ci.yml', 'main.yml']
      break
    case ModuleType.KONG:
      // folio-kong uses: do-docker.yml, test.yml
      workflowNames = ['do-docker.yml', 'test.yml', 'build.yml', 'ci.yml']
      break
    case ModuleType.KEYCLOAK:
      // folio-keycloak uses: do-docker.yml
      // mod-*-keycloak modules might use different workflows
      if (repositoryName == 'folio-keycloak') {
        workflowNames = ['do-docker.yml', 'build.yml', 'ci.yml']
      } else {
        // For mod-*-keycloak modules
        workflowNames = ['build.yml', 'ci.yml', 'maven.yml', 'do-docker.yml']
      }
      break
    default:
      workflowNames = ['build.yml', 'ci.yml', 'main.yml']
  }
  
  echo "Searching for GitHub workflow run #${buildId} in repository ${repositoryName}"
  echo "Trying workflow files: ${workflowNames}"
  
  for (String workflowName : workflowNames) {
    try {
      echo "Checking workflow: ${workflowName}"
      def workflowRun = gitHubClient.getWorkflowRunByNumber(repositoryName, workflowName, buildId)
      if (workflowRun?.head_sha) {
        echo "Found workflow run #${buildId} in ${workflowName}: SHA ${workflowRun.head_sha}"
        return workflowRun.head_sha
      }
    } catch (Exception e) {
      echo "Workflow ${workflowName} check failed: ${e.getMessage()}"
    }
  }
  
  echo "No workflow run found for build #${buildId} in any of: ${workflowNames}"
  return 'Unknown'
}

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
