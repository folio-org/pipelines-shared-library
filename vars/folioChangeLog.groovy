import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.ChangelogEntry
import org.folio.models.module.FolioModule
import org.folio.models.module.ModuleType
import org.folio.slack.SlackHelper
import org.folio.utilities.GitHubClient
import org.folio.far.Far
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
      Far far = new Far(this)
      Map descriptor = far.getApplicationDescriptor(newId, true)
      if (descriptor?.moduleDescriptors) {
        modules = descriptor.moduleDescriptors.collect { it.id ?: it.moduleId ?: it.name }.findAll { it }
      } else if (descriptor?.modules) {
        modules = descriptor.modules.collect { it.id ?: it.moduleId ?: it.name }.findAll { it }
      } else {
        // try to find any arrays with objects containing id or moduleId
        def collector = []
        descriptor.each { k, v ->
          if (v instanceof List) {
            v.each { el -> if (el instanceof Map) collector << el }
          }
        }
        modules = collector.collect { it.id ?: it.moduleId ?: it.name }.findAll { it }
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
}


List getUpdatedAppsList(Map commitInfo, String filename = 'platform-descriptor.json') {
  try {
    // (?m) multiline so ^ and $ match line boundaries; match a removed id line immediately
    // followed by optional context lines then an added id line
    String pattern = /(?m)^-[^\n]*"id"\s*:\s*"([^"]+)"[^\n]*\n(?:[^+-][^\n]*\n)*\+[^\n]*"id"\s*:\s*"([^"]+)"/
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
