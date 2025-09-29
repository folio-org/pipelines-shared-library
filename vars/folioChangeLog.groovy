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
          // First try to get SHA from Jenkins build in the current instance
          changeLogEntry.sha = getJenkinsBuildSha(repositoryName, module.buildId.toInteger())
          if (!changeLogEntry.sha) {
            echo "Warning: Could not find Jenkins build SHA for ${repositoryName} build #${module.buildId} in current Jenkins instance"
            // Try to get SHA from external Jenkins instance (jenkins-aws.indexdata.com)
            echo "Attempting to get SHA from external Jenkins via REST API for ${repositoryName} build #${module.buildId}"
            try {
              String externalResult = getExternalJenkinsBuildSha(repositoryName, module.buildId.toInteger())
              
              // Check if we got a corrected repository name along with the SHA
              if (externalResult && externalResult.contains('|')) {
                def parts = externalResult.split('\\|')
                changeLogEntry.sha = parts[0]
                String correctedRepoName = parts[1]
                echo "🔄 Using corrected repository name '${correctedRepoName}' instead of '${repositoryName}' for GitHub API calls"
                repositoryName = correctedRepoName // Update repository name for subsequent GitHub calls
              } else {
                changeLogEntry.sha = externalResult
              }
              
              if (changeLogEntry.sha && changeLogEntry.sha != 'Unknown') {
                echo "Successfully found SHA from external Jenkins: ${changeLogEntry.sha} for ${repositoryName} build #${module.buildId}"
              } else {
                echo "Could not find SHA in external Jenkins for ${repositoryName} build #${module.buildId}"
              }
            } catch (Exception extJenkinsException) {
              echo "External Jenkins lookup failed for ${repositoryName}: ${extJenkinsException.getMessage()}"
            }
          }
          
          if (!changeLogEntry.sha || changeLogEntry.sha == 'Unknown') {
            // For Eureka modules, try GitHub workflow runs as fallback
            echo "Attempting GitHub workflow fallback for ${repositoryName} build #${module.buildId}"
            try {
              // Try different common workflow names for backend modules
              def workflowNames = ['build.yml', 'ci.yml', 'build-snapshot.yml', 'maven.yml', 'java.yml']
              def workflowRun = null
              
              for (String workflowName : workflowNames) {
                try {
                  echo "Trying workflow: ${workflowName} for ${repositoryName} build #${module.buildId}"
                  workflowRun = gitHubClient.getWorkflowRunByNumber(repositoryName, workflowName, module.buildId)
                  if (workflowRun?.head_sha) {
                    echo "Found workflow run in ${workflowName} for ${repositoryName} build #${module.buildId}"
                    break
                  }
                } catch (Exception wfException) {
                  echo "Workflow ${workflowName} not found or failed for ${repositoryName}: ${wfException.getMessage()}"
                }
              }
              
              changeLogEntry.sha = workflowRun?.head_sha ?: 'Unknown'
              if (changeLogEntry.sha != 'Unknown') {
                echo "Successfully found GitHub workflow SHA ${changeLogEntry.sha} for ${repositoryName} build #${module.buildId}"
              } else {
                echo "Could not find workflow run in any of: ${workflowNames} for ${repositoryName} build #${module.buildId}"
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
        echo "Looking for GitHub workflow run for repository: ${repositoryName}, build: ${module.buildId}"
        try {
          // Try different common workflow names for UI modules
          def workflowNames = ['ui.yml', 'build-npm.yml', 'build.yml', 'ci.yml']
          def workflowRun = null
          
          for (String workflowName : workflowNames) {
            try {
              echo "Trying workflow: ${workflowName} for ${repositoryName} build #${module.buildId}"
              workflowRun = gitHubClient.getWorkflowRunByNumber(repositoryName, workflowName, module.buildId)
              if (workflowRun?.head_sha) {
                echo "Found workflow run in ${workflowName} for ${repositoryName} build #${module.buildId}"
                break
              }
            } catch (Exception wfException) {
              echo "Workflow ${workflowName} not found or failed for ${repositoryName}: ${wfException.getMessage()}"
            }
          }
          
          changeLogEntry.sha = workflowRun?.head_sha ?: null
          if (!changeLogEntry.sha) {
            echo "Warning: Could not find workflow run SHA for ${repositoryName} build #${module.buildId} in any of: ${workflowNames}"
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
        echo "Fetching commit info for SHA: ${changeLogEntry.sha} from repository: ${repositoryName}"
        
        // First verify the SHA exists in GitHub
        boolean shaExists = verifyGitHubCommitExists(repositoryName, changeLogEntry.sha)
        
        if (shaExists) {
          echo "SHA verified in GitHub, proceeding to fetch full commit info..."
          commitInfo = gitHubClient.getCommitInfo(changeLogEntry.sha, repositoryName)
          if (commitInfo?.commit?.author?.name) {
            echo "✅ Successfully fetched commit info for ${repositoryName} SHA ${changeLogEntry.sha}: ${commitInfo.commit.message?.split('\n', 2)?.getAt(0)} by ${commitInfo.commit.author.name}"
          } else {
            echo "⚠️ Commit info returned but missing expected fields for ${repositoryName} SHA ${changeLogEntry.sha}"
          }
        } else {
          echo "❌ SHA ${changeLogEntry.sha} does not exist in GitHub repository ${repositoryName}"
          echo "This suggests the SHA from Jenkins belongs to a different repository or branch"
        }
      } else {
        echo "Warning: SHA is null or 'Unknown' for module ${module.name} (${module.type}). Build ID: ${module.buildId}"
      }
    } catch (Exception e) {
      echo "Error fetching commit info for SHA: ${changeLogEntry.sha}, repository: ${repositoryName}. Error: ${e.getMessage()}"
      echo "This could be due to: 1) SHA not existing in GitHub repo, 2) Network issues, 3) API rate limits, 4) Repository access issues"
      // Don't fail the entire process, just continue with limited info
    }

    changeLogEntry.author = commitInfo?.commit?.author?.name ?: 'Unknown author'
    
    if (changeLogEntry.sha == 'Unknown') {
      if (module.type == ModuleType.FRONTEND) {
        changeLogEntry.commitMessage = "Unable to find GitHub workflow run ${module.buildId} for ${repositoryName}"
      } else {
        // For backend modules, provide more detailed error information
        def jobExists = checkJenkinsJobExists(repositoryName)
        String jenkinsBaseUrl = env.BUILD_URL ? env.BUILD_URL.replaceAll(/\/job\/.*$/, '') : 'current Jenkins instance'
        if (!jobExists) {
          changeLogEntry.commitMessage = "Jenkins job not found at ${jenkinsBaseUrl}/job/folio-org/job/${repositoryName}/job/master/ - possible Eureka-only module"
        } else {
          changeLogEntry.commitMessage = "Jenkins build ${module.buildId} not found for ${repositoryName} at ${jenkinsBaseUrl} - build may not exist"
        }
      }
    } else {
      // We have a SHA, try to get commit message
      if (commitInfo?.commit?.message) {
        changeLogEntry.commitMessage = commitInfo.commit.message.split('\n', 2)?.getAt(0)
      } else {
        // We have SHA but no commit info - this is the case you're seeing
        changeLogEntry.commitMessage = "Build ${module.buildId} (SHA: ${changeLogEntry.sha.take(7)}) - unable to fetch commit details from GitHub"
      }
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
    String jenkinsBaseUrl = env.BUILD_URL ? env.BUILD_URL.replaceAll(/\/job\/.*$/, '') : 'Jenkins instance'
    String fullJobUrl = "${jenkinsBaseUrl}/job/folio-org/job/${moduleName}/job/master/"
    
    Job moduleJob = Jenkins.instance.getItemByFullName(jobPath)
    if (moduleJob == null) {
      echo "Jenkins job not found - checked path: ${jobPath} (${fullJobUrl})"
    }
    return moduleJob != null
  } catch (Exception e) {
    echo "Error checking Jenkins job existence for ${moduleName}: ${e.getMessage()}"
    return false
  }
}

String extractRepoNameFromGitUrl(String gitUrl) {
  try {
    if (!gitUrl || gitUrl == 'Unknown') {
      return null
    }
    
    // Handle both SSH and HTTPS Git URLs
    // https://github.com/folio-org/jenkins-pipeline-libs.git -> jenkins-pipeline-libs
    // git@github.com:folio-org/jenkins-pipeline-libs.git -> jenkins-pipeline-libs
    
    def match = gitUrl =~ /\/folio-org\/([^\/]+?)(?:\.git)?\/?$/
    if (match) {
      String repoName = match[0][1]
      echo "📋 Extracted repository name '${repoName}' from Git URL: ${gitUrl}"
      return repoName
    }
    
    echo "⚠️ Could not extract repository name from Git URL: ${gitUrl}"
    return null
  } catch (Exception e) {
    echo "Error extracting repository name from Git URL ${gitUrl}: ${e.getMessage()}"
    return null
  }
}

boolean verifyGitHubCommitExists(String repositoryName, String sha) {
  try {
    echo "Verifying if SHA ${sha} exists in GitHub repository: ${repositoryName}"
    
    // Try to get just the commit without full details first
    def response = sh(
      script: "curl -s -o /dev/null -w '%{http_code}' 'https://api.github.com/repos/folio-org/${repositoryName}/commits/${sha}'",
      returnStdout: true
    ).trim()
    
    echo "GitHub API response code for ${repositoryName}/commits/${sha}: ${response}"
    
    if (response == '200') {
      echo "✅ SHA ${sha} exists in repository ${repositoryName}"
      return true
    } else if (response == '404') {
      echo "❌ SHA ${sha} not found in repository ${repositoryName} (HTTP 404)"
      return false
    } else if (response == '403') {
      echo "⚠️ Access denied to repository ${repositoryName} or rate limited (HTTP 403)"
      return false
    } else {
      echo "⚠️ Unexpected response ${response} when checking ${repositoryName}/commits/${sha}"
      return false
    }
  } catch (Exception e) {
    echo "Error verifying GitHub commit existence: ${e.getMessage()}"
    return false
  }
}

String getExternalJenkinsBuildSha(String moduleName, int moduleBuildId) {
  Logger logger = new Logger(this, 'getExternalJenkinsBuildSha')
  
  if (!moduleBuildId || moduleBuildId <= 0) {
    logger.warning("Invalid module build ID: ${moduleBuildId} for module: ${moduleName}")
    return 'Unknown'
  }
  
  try {
    String jenkinsBaseUrl = "https://jenkins-aws.indexdata.com"
    String buildApiUrl = "${jenkinsBaseUrl}/job/folio-org/job/${moduleName}/job/master/${moduleBuildId}/api/json"
    
    logger.info("Attempting to get build info from external Jenkins: ${buildApiUrl}")
    
    def response = sh(
      script: "curl -s -f '${buildApiUrl}'",
      returnStdout: true
    ).trim()
    
    if (!response) {
      logger.warning("Empty response from external Jenkins API for ${moduleName} build #${moduleBuildId}")
      return 'Unknown'
    }
    
    def buildInfo = readJSON text: response
    
    // Look for Git-related actions in the build
    def gitAction = buildInfo.actions?.find { action ->
      action._class?.contains('BuildData') || action.lastBuiltRevision?.SHA1
    }
    
    if (gitAction?.lastBuiltRevision?.SHA1) {
      String sha = gitAction.lastBuiltRevision.SHA1
      
      // Also try to get the Git repository URL for debugging
      String gitUrl = 'Unknown'
      if (gitAction.remoteUrls) {
        gitUrl = gitAction.remoteUrls[0] ?: 'Unknown'
      }
      
      logger.info("✅ Successfully retrieved SHA ${sha} from external Jenkins for ${moduleName} build #${moduleBuildId}")
      logger.info("📍 Git repository URL from Jenkins: ${gitUrl}")
      
      // Validate that the Git URL matches what we expect
      String expectedUrl = "https://github.com/folio-org/${moduleName}.git"
      if (gitUrl != expectedUrl && gitUrl != 'Unknown') {
        logger.warning("⚠️ Git URL mismatch! Expected: ${expectedUrl}, Found: ${gitUrl}")
        logger.warning("This might explain why SHA ${sha} is not found in GitHub repository ${moduleName}")
        
        // Try to extract the actual repository name from the Git URL
        String actualRepoName = extractRepoNameFromGitUrl(gitUrl)
        if (actualRepoName && actualRepoName != moduleName) {
          logger.info("🔄 Extracted actual repository name '${actualRepoName}' from Git URL")
          // Return both SHA and the corrected repository name
          return "${sha}|${actualRepoName}"
        }
      }
      
      return sha
    } else {
      logger.warning("No Git SHA found in external Jenkins build data for ${moduleName} build #${moduleBuildId}")
      logger.warning("Available actions: ${buildInfo.actions?.collect { it._class }}")
      return 'Unknown'
    }
    
  } catch (Exception e) {
    logger.warning("Exception getting build SHA from external Jenkins for ${moduleName} build #${moduleBuildId}: ${e.getMessage()}")
    return 'Unknown'
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
    String jenkinsBaseUrl = env.BUILD_URL ? env.BUILD_URL.replaceAll(/\/job\/.*$/, '') : 'Jenkins instance'
    String fullJobUrl = "${jenkinsBaseUrl}/job/folio-org/job/${moduleName}/job/master/"
    
    Job moduleJob = Jenkins.instance.getItemByFullName(jobPath)
    if (moduleJob == null) {
      logger.warning("Jenkins job not found at path: ${jobPath}")
      logger.warning("Full job URL would be: ${fullJobUrl}")
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
