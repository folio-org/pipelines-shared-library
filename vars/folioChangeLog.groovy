import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.ChangelogEntry
import org.folio.models.module.FolioModule
import org.folio.models.module.ModuleType
import org.folio.slack.SlackHelper
import org.folio.utilities.GitHubClient
import java.util.regex.Matcher

List<ChangelogEntry> call(String previousSha, String currentSha) {

  GitHubClient gitHubClient = new GitHubClient(this)
  String platformRepositoryName = 'platform-lsp'

  List allChangeLogShas = gitHubClient.getTwoCommitsDiff(previousSha, currentSha, platformRepositoryName)['commits']
    .collect { it['sha'] }

  List allPlatformDescriptorChangeLogShas = gitHubClient.getFileChangeHistory(currentSha, 'platform-descriptor.json', platformRepositoryName)
    .collect { it['sha'] }

  List platformDescriptorChangeLogShas = allChangeLogShas.intersect(allPlatformDescriptorChangeLogShas)

  List updatedApps = []

  echo "Processing platform-descriptor.json changes: ${platformDescriptorChangeLogShas.size()} commits"
  platformDescriptorChangeLogShas.each { sha ->
    updatedApps.addAll(getUpdatedAppsList(gitHubClient.getCommitInfo(sha, platformRepositoryName), 'platform-descriptor.json'))
  }

  echo "Total apps found: ${updatedApps.size()}"

  Map<String, ChangelogEntry> changeLogEntriesMap = [:]
  updatedApps.each { appChange ->
    // appChange is a Map [oldId:..., newId:...]
    String oldId = appChange.oldId
    String newId = appChange.newId

    ChangelogEntry changeLogEntry = new ChangelogEntry()
    // Use module.id to hold application id
    changeLogEntry.module = new FolioModule()
    changeLogEntry.module.id = newId

    // Parse app name and versions
    def splitIndexOld = oldId?.lastIndexOf('-')
    def splitIndexNew = newId?.lastIndexOf('-')
    String oldName = splitIndexOld > 0 ? oldId[0..(splitIndexOld - 1)] : oldId
    String oldVersion = splitIndexOld > 0 ? oldId[(splitIndexOld + 1)..-1] : ''
    String newName = splitIndexNew > 0 ? newId[0..(splitIndexNew - 1)] : newId
    String newVersion = splitIndexNew > 0 ? newId[(splitIndexNew + 1)..-1] : ''

    // Fetch application descriptor from FAR to extract modules
    List modules = []
    try {
      String farUrl = "https://far.ci.folio.org/applications/${newId}"
      String resp = sh(script: "curl -s --fail '${farUrl}'", returnStdout: true).trim()
      if (resp) {
        def json = new groovy.json.JsonSlurper().parseText(resp)
        // Common keys where module descriptors may exist
        if (json.moduleDescriptors) {
          modules = json.moduleDescriptors.collect { it.id ?: it.moduleId ?: it.name }.findAll { it }
        } else if (json.modules) {
          modules = json.modules.collect { it.id ?: it.moduleId ?: it.name }.findAll { it }
        } else {
          // try to find any arrays with objects containing id or moduleId
          def collector = []
          json.each { k, v ->
            if (v instanceof List) {
              v.each { el -> if (el instanceof Map) collector << el }
            }
          }
          modules = collector.collect { it.id ?: it.moduleId ?: it.name }.findAll { it }
        }
      }
    } catch (Exception e) {
      echo "Failed to fetch FAR descriptor for ${newId}: ${e.message}"
    }

    // Build commitMessage in the requested format
    StringBuilder messageBuilder = new StringBuilder()
    messageBuilder.append("${oldName}(${oldVersion}) --> ${newName}(${newVersion})\n")
    messageBuilder.append('Changed modules:\n')
    if (modules && modules.size() > 0) {
      modules.eachWithIndex { mod, idx ->
        messageBuilder.append("${idx + 1}. ${mod}\n")
      }
    } else {
      messageBuilder.append('No modules found or failed to fetch descriptor\n')
    }

    changeLogEntry.sha = newId
    changeLogEntry.commitMessage = messageBuilder.toString()
    changeLogEntry.author = null
    changeLogEntry.commitLink = "https://far.ci.folio.org/applications/${newId}"

    String entryKey = "${newId}|${changeLogEntry.sha}"
    changeLogEntriesMap[entryKey] = changeLogEntry
  }

  return changeLogEntriesMap.values().toList()
      case ModuleType.BACKEND:
      case ModuleType.EDGE:
      case ModuleType.MGR:
      case ModuleType.SIDECAR:
        repositoryName = module.name
        def backendWorkflowFile = 'maven.yml'
        echo "Looking for GitHub workflow run for repository: ${repositoryName}, workflow: ${backendWorkflowFile}, build: ${module.buildId}"
        try {
          def workflowRun = gitHubClient.getWorkflowRunByNumber(repositoryName, backendWorkflowFile, module.buildId)
          changeLogEntry.sha = workflowRun?.head_sha ?: null
          if (!changeLogEntry.sha) {
            echo "Warning: Could not find workflow run #${module.buildId}, falling back to master branch"
            def branchInfo = gitHubClient.getBranchInfo(repositoryName, 'master')
            changeLogEntry.sha = branchInfo?.commit?.sha ?: 'Unknown'
          } else {
            echo "Successfully found SHA ${changeLogEntry.sha} for ${repositoryName} build #${module.buildId}"
          }
        } catch (Exception e) {
          echo "Error getting workflow run SHA for ${repositoryName} build #${module.buildId}: ${e.getMessage()}"
          echo "Falling back to master branch"
          try {
            def branchInfo = gitHubClient.getBranchInfo(repositoryName, 'master')
            changeLogEntry.sha = branchInfo?.commit?.sha ?: 'Unknown'
          } catch (Exception e2) {
            echo "Error getting master branch SHA: ${e2.getMessage()}"
            changeLogEntry.sha = 'Unknown'
          }
        }
        break
      case ModuleType.KONG:
      case ModuleType.KEYCLOAK:
        repositoryName = module.name
        def workflowFile = 'do-docker.yml'
        echo "Looking for GitHub workflow run for repository: ${repositoryName}, workflow: ${workflowFile}, build: ${module.buildId}"
        try {
          def workflowRun = gitHubClient.getWorkflowRunByNumber(repositoryName, workflowFile, module.buildId)
          changeLogEntry.sha = workflowRun?.head_sha ?: null
          if (!changeLogEntry.sha) {
            echo "Warning: Could not find workflow run #${module.buildId}, falling back to master branch"
            def branchInfo = gitHubClient.getBranchInfo(repositoryName, 'master')
            changeLogEntry.sha = branchInfo?.commit?.sha ?: 'Unknown'
          } else {
            echo "Successfully found SHA ${changeLogEntry.sha} for ${repositoryName} build #${module.buildId}"
          }
        } catch (Exception e) {
          echo "Error getting workflow run SHA for ${repositoryName} build #${module.buildId}: ${e.getMessage()}"
          echo "Falling back to master branch"
          try {
            def branchInfo = gitHubClient.getBranchInfo(repositoryName, 'master')
            changeLogEntry.sha = branchInfo?.commit?.sha ?: 'Unknown'
          } catch (Exception e2) {
            echo "Error getting master branch SHA: ${e2.getMessage()}"
            changeLogEntry.sha = 'Unknown'
          }
        }
        break
      case ModuleType.FRONTEND:
        repositoryName = "ui-${module.name.replaceFirst('folio_', '')}"
        def frontendWorkflowFile = 'build-npm.yml'
        echo "Looking for GitHub workflow run for repository: ${repositoryName}, workflow: ${frontendWorkflowFile}, build: ${module.buildId}"
        try {
          def workflowRun = gitHubClient.getWorkflowRunByNumber(repositoryName, frontendWorkflowFile, module.buildId)
          changeLogEntry.sha = workflowRun?.head_sha ?: null
          if (!changeLogEntry.sha) {
            echo "Warning: Could not find workflow run #${module.buildId}, falling back to master branch"
            def branchInfo = gitHubClient.getBranchInfo(repositoryName, 'master')
            changeLogEntry.sha = branchInfo?.commit?.sha ?: 'Unknown'
          } else {
            echo "Successfully found SHA ${changeLogEntry.sha} for ${repositoryName} build #${module.buildId}"
          }
        } catch (Exception e) {
          echo "Error getting workflow run SHA for ${repositoryName} build #${module.buildId}: ${e.getMessage()}"
          echo "Falling back to master branch"
          try {
            def branchInfo = gitHubClient.getBranchInfo(repositoryName, 'master')
            changeLogEntry.sha = branchInfo?.commit?.sha ?: 'Unknown'
          } catch (Exception e2) {
            echo "Error getting master branch SHA: ${e2.getMessage()}"
            changeLogEntry.sha = 'Unknown'
          }
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
      changeLogEntry.commitMessage = "Unable to find GitHub workflow run ${module.buildId} for ${repositoryName}"
    } else {
      changeLogEntry.commitMessage = commitInfo?.commit?.message?.split('\n', 2)?.getAt(0) ?: "Unable to fetch commit info for ${module.name} (build: ${module.buildId})"
    }

    changeLogEntry.commitLink = commitInfo?.html_url ?: null

    String entryKey = "${module.name}|${changeLogEntry.sha ?: 'Unknown'}"
    if (!changeLogEntriesMap.containsKey(entryKey)) {
      changeLogEntriesMap[entryKey] = changeLogEntry
    } else {
      ChangelogEntry existingEntry = changeLogEntriesMap[entryKey]
      String existingBuildId = existingEntry?.module?.buildId?.toString()
      String newBuildId = module?.buildId?.toString()
      Integer existingBuildNumber = existingBuildId?.isInteger() ? existingBuildId.toInteger() : null
      Integer newBuildNumber = newBuildId?.isInteger() ? newBuildId.toInteger() : null

      if (newBuildNumber != null && existingBuildNumber != null && newBuildNumber > existingBuildNumber) {
        changeLogEntriesMap[entryKey] = changeLogEntry
      }
    }
  }

  return changeLogEntriesMap.values().toList()
}

static List getUpdatedAppsList(Map commitInfo, String filename = 'platform-descriptor.json') {
  try {
    // Dotall to allow matching across newlines
    String pattern = /(?s)-\s*"id"\s*:\s*"(.*?)".*?\+\s*"id"\s*:\s*"(.*?)"/
    def fileInfo = commitInfo['files']?.find { it['filename'] == filename }

    if (!fileInfo || !fileInfo['patch']) {
      return []
    }

    Matcher matches = fileInfo['patch'] =~ pattern
    return matches.collect { match -> [oldId: match[1], newId: match[2]] }
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

String getPlainText(List<ChangelogEntry> changeLogEntriesList) {
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
