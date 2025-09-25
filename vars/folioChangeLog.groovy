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
    String repositoryName
    changeLogEntry.module = module

    switch (module.type) {
      case ModuleType.BACKEND:
      case ModuleType.EDGE:
      case ModuleType.MGR:
      case ModuleType.SIDECAR:
      case ModuleType.KONG:
      case ModuleType.KEYCLOAK:
        repositoryName = module.name
        try {
          // First try to get SHA from Jenkins build
          changeLogEntry.sha = getJenkinsBuildSha(repositoryName, module.buildId.toInteger())
          if (!changeLogEntry.sha) {
            echo "Warning: Could not find Jenkins build SHA for ${repositoryName} build #${module.buildId}"
            // For Eureka modules, try GitHub workflow runs as fallback
            echo "Attempting GitHub workflow fallback for ${repositoryName} build #${module.buildId}"
            try {
              GitHubClient gitHubClient = new GitHubClient(this)
              def workflowRun = gitHubClient.getWorkflowRunByNumber(repositoryName, 'build.yml', module.buildId)
              if (!workflowRun) {
                // Try alternative workflow names for backend modules
                workflowRun = gitHubClient.getWorkflowRunByNumber(repositoryName, 'build-snapshot.yml', module.buildId)
              }
              changeLogEntry.sha = workflowRun?.head_sha ?: 'Unknown'
              if (changeLogEntry.sha != 'Unknown') {
                echo "Successfully found GitHub workflow SHA ${changeLogEntry.sha} for ${repositoryName} build #${module.buildId}"
              }
            } catch (Exception ghException) {
              echo "GitHub workflow fallback also failed for ${repositoryName}: ${ghException.getMessage()}"
              changeLogEntry.sha = 'Unknown'
            }
          }
        } catch (Exception e) {
          echo "Error getting Jenkins build SHA for ${repositoryName} build #${module.buildId}: ${e.getMessage()}"
          changeLogEntry.sha = 'Unknown'
        }
        break
      case ModuleType.FRONTEND:
        repositoryName = module.name.replace('folio_', 'ui-')
        echo "Looking for GitHub workflow run for repository: ${repositoryName}, workflow: build-npm.yml, build: ${module.buildId}"
        try {
          def workflowRun = gitHubClient.getWorkflowRunByNumber(repositoryName, 'build-npm.yml', module.buildId)
          changeLogEntry.sha = workflowRun?.head_sha ?: null
          if (!changeLogEntry.sha) {
            echo "Warning: Could not find workflow run SHA for ${repositoryName} build #${module.buildId}"
            echo "Workflow run response: ${workflowRun}"
            changeLogEntry.sha = 'Unknown'
          } else {
            echo "Successfully found SHA ${changeLogEntry.sha} for ${repositoryName} build #${module.buildId}"
          }
        } catch (Exception e) {
          echo "Error getting workflow run SHA for ${repositoryName} build #${module.buildId}: ${e.getMessage()}"
          changeLogEntry.sha = 'Unknown'
        }
        break
      default:
        echo "Warning: Unknown module type ${module.type} for module ${module.name}. Skipping SHA lookup."
        repositoryName = module.name
        changeLogEntry.sha = 'Unknown'
        break
    }

    Map commitInfo = [:]
    try {
      if (changeLogEntry.sha && changeLogEntry.sha != 'Unknown') {
        commitInfo = gitHubClient.getCommitInfo(changeLogEntry.sha, repositoryName)
      } else {
        echo "Warning: SHA is null or 'Unknown' for module ${module.name} (${module.type}). Build ID: ${module.buildId}"
      }
    } catch (Exception e) {
      echo "Error fetching commit info for SHA: ${changeLogEntry.sha}, repository: ${repositoryName}. Error: ${e.getMessage()}"
    }

    changeLogEntry.author = commitInfo?.commit?.author?.name ?: 'Unknown author'
    
    if (changeLogEntry.sha == 'Unknown') {
      if (module.type == ModuleType.FRONTEND) {
        changeLogEntry.commitMessage = "Unable to find GitHub workflow run ${module.buildId} for ${repositoryName}"
      } else {
        // For backend modules, provide more detailed error information
        def jobExists = checkJenkinsJobExists(repositoryName)
        if (!jobExists) {
          changeLogEntry.commitMessage = "Jenkins job not found at /folio-org/${repositoryName}/master - possible Eureka-only module"
        } else {
          changeLogEntry.commitMessage = "Jenkins build ${module.buildId} not found for ${repositoryName} - build may not exist"
        }
      }
    } else {
      changeLogEntry.commitMessage = commitInfo?.commit?.message?.split('\n', 2)?.getAt(0) ?: "Unable to fetch commit info for ${module.name} (build: ${module.buildId})"
    }
    
    changeLogEntry.commitLink = commitInfo?.html_url ?: null

    changeLogEntriesList << changeLogEntry
  }

  return changeLogEntriesList
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

boolean checkJenkinsJobExists(String moduleName) {
  try {
    String jobPath = "/folio-org/${moduleName}/master"
    Job moduleJob = Jenkins.instance.getItemByFullName(jobPath)
    return moduleJob != null
  } catch (Exception e) {
    echo "Error checking Jenkins job existence for ${moduleName}: ${e.getMessage()}"
    return false
  }
}

String getJenkinsBuildSha(String moduleName, int moduleBuildId) {
  Logger logger = new Logger(this, 'getJenkinsBuildSha')

  if (!moduleBuildId || moduleBuildId <= 0) {
    logger.warning("Invalid module build ID: ${moduleBuildId} for module: ${moduleName}")
    return null
  }

  try {
    String jobPath = "/folio-org/${moduleName}/master"
    Job moduleJob = Jenkins.instance.getItemByFullName(jobPath)
    if (moduleJob == null) {
      logger.warning("Jenkins job not found at path: ${jobPath}")
      return null
    }

    Run moduleBuild = moduleJob.getBuildByNumber(moduleBuildId)
    if (moduleBuild == null) {
      logger.warning("Build #${moduleBuildId} not found for job: ${jobPath}")
      return null
    }

    Action moduleBuildAction = moduleBuild.getActions(BuildData).find { action -> 
      action.getRemoteUrls()?.size() > 0 && action.getRemoteUrls()[0] == "https://github.com/folio-org/${moduleName}.git"
    }
    if (moduleBuildAction == null) {
      logger.warning("No BuildData action found with GitHub remote URL for ${jobPath} build #${moduleBuildId}")
      logger.warning("Available BuildData actions: ${moduleBuild.getActions(BuildData).collect { it.getRemoteUrls() }}")
      return null
    }

    String sha = moduleBuildAction.getLastBuiltRevision()?.sha1?.name
    if (!sha) {
      logger.warning("No SHA found in BuildData for ${jobPath} build #${moduleBuildId}")
      return null
    }

    logger.info("Successfully retrieved SHA ${sha} for ${jobPath} build #${moduleBuildId}")
    return sha
  } catch (Exception e) {
    logger.warning("Exception getting build SHA for ${moduleName} build #${moduleBuildId}: ${e.getMessage()}")
    logger.warning("Exception stack trace: ${e.getStackTrace()}")
    return null
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
