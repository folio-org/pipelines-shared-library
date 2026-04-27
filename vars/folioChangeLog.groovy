import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import org.folio.models.ChangelogEntry
import org.folio.models.module.FolioModule
import org.folio.slack.SlackHelper
import org.folio.utilities.GitHubClient
import org.folio.far.Far

List<ChangelogEntry> call(String previousSha, String currentSha) {

  GitHubClient gitHubClient = new GitHubClient(this)
  String platformRepo = 'platform-lsp'
  String descriptorFile = 'platform-descriptor.json'

  String previousContent = gitHubClient.getFileContent(previousSha, descriptorFile, platformRepo)
  String currentContent = gitHubClient.getFileContent(currentSha, descriptorFile, platformRepo)

  if (!previousContent || !currentContent) {
    echo "Could not fetch ${descriptorFile} at one or both SHAs (previous=${previousSha}, current=${currentSha})"
    return []
  }

  List<String> previousAppIds = parseAppIds(previousContent)
  List<String> currentAppIds = parseAppIds(currentContent)

  echo "Previous app count: ${previousAppIds.size()}, Current app count: ${currentAppIds.size()}"

  List<Map> changedApps = diffApps(previousAppIds, currentAppIds)
  echo "Changed apps: ${changedApps.size()}"

  Map<String, ChangelogEntry> changeLogEntriesMap = [:]

  changedApps.each { appChange ->
    String oldId = appChange.oldId
    String newId = appChange.newId

    ChangelogEntry changeLogEntry = new ChangelogEntry()
    changeLogEntry.module = new FolioModule()
    changeLogEntry.module.id = newId

    List<String> modules = []
    try {
      Far far = new Far(this)
      Map descriptor = far.getApplicationDescriptor(newId, true)
      modules = extractModuleIds(descriptor)
    } catch (Exception e) {
      echo "Failed to fetch FAR descriptor for ${newId}: ${e.message}"
    }

    String appName = extractName(newId)
    String oldVersion = extractVersion(oldId)
    String newVersion = extractVersion(newId)

    StringBuilder messageBuilder = new StringBuilder()
    messageBuilder.append("${appName}(${oldVersion}) --> ${appName}(${newVersion})\n")
    messageBuilder.append("Changed modules:\n")
    if (modules) {
      modules.eachWithIndex { mod, idx -> messageBuilder.append("${idx + 1}. ${mod}\n") }
    } else {
      messageBuilder.append("No modules found\n")
    }

    changeLogEntry.sha = newId
    changeLogEntry.commitMessage = messageBuilder.toString()
    changeLogEntry.author = null
    changeLogEntry.commitLink = "https://far.ci.folio.org/applications/${newId}"

    changeLogEntriesMap[newId] = changeLogEntry
  }

  return changeLogEntriesMap.values().toList()
}

/**
 * Parse platform-descriptor.json and return list of application IDs (name-version).
 * The file structure is:
 * {
 *   "applications": {
 *     "required":     [{"name": "app-x", "version": "1.0.0"}, ...],
 *     "optional":     [...],
 *     "experimental": [...]
 *   }
 * }
 */
@NonCPS
static List<String> parseAppIds(String json) {
  try {
    def parsed = new JsonSlurper().parseText(json)
    List<String> ids = []

    if (parsed instanceof Map && parsed.applications instanceof Map) {
      ['required', 'optional', 'experimental'].each { category ->
        def apps = parsed.applications[category]
        if (apps instanceof List) {
          apps.each { app ->
            if (app instanceof Map && app.name && app.version) {
              ids << "${app.name}-${app.version}".toString()
            }
          }
        }
      }
    }

    return ids
  } catch (Exception e) {
    return []
  }
}

/**
 * Return list of [oldId, newId] maps for apps whose version changed between builds.
 */
@NonCPS
static List<Map> diffApps(List<String> previousIds, List<String> currentIds) {
  Map<String, String> prevByName = [:]
  previousIds.each { id ->
    String name = extractName(id)
    if (name) prevByName[name] = id
  }

  List<Map> changes = []
  currentIds.each { newId ->
    String name = extractName(newId)
    String oldId = prevByName[name]
    if (oldId && oldId != newId) {
      changes << [oldId: oldId, newId: newId]
    }
  }
  return changes
}

/**
 * Extract the app name from an ID like "app-platform-minimal-1.0.0-SNAPSHOT.123".
 * The name is everything before the first dash followed by a digit.
 */
@NonCPS
static String extractName(String appId) {
  if (!appId) return ''
  for (int i = 0; i < appId.length() - 1; i++) {
    if (appId.charAt(i) == '-' as char && Character.isDigit(appId.charAt(i + 1))) {
      return appId[0..(i - 1)]
    }
  }
  return appId
}

/**
 * Extract the version part from an app ID.
 */
@NonCPS
static String extractVersion(String appId) {
  if (!appId) return ''
  String name = extractName(appId)
  return (name && name.length() < appId.length()) ? appId[(name.length() + 1)..-1] : ''
}

/**
 * Extract module IDs from a FAR application descriptor.
 * The descriptor has "modules" (backend) and "uiModules" (frontend) arrays.
 */
@NonCPS
static List<String> extractModuleIds(Map descriptor) {
  if (!descriptor) return []
  List<String> modules = []
  ['modules', 'uiModules'].each { key ->
    if (descriptor[key] instanceof List) {
      descriptor[key].each { mod ->
        String id = mod?.id ?: mod?.moduleId
        if (id) modules << id
      }
    }
  }
  return modules.unique()
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
