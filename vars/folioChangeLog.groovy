import com.cloudbees.groovy.cps.NonCPS
import org.folio.far.Far
import org.folio.slack.SlackHelper
import org.folio.utilities.GitHubClient

Map<String, Map> call(String previousSha, String currentSha) {

  GitHubClient gitHubClient = new GitHubClient(this)
  String platformLspRepositoryName = 'platform-lsp'
  Far far = new Far(this)

  echo "Fetching platform descriptors from ${platformLspRepositoryName}"
  Map previousPlatformDescriptor = getPlatformDescriptor(gitHubClient, previousSha, platformLspRepositoryName)
  Map currentPlatformDescriptor = getPlatformDescriptor(gitHubClient, currentSha, platformLspRepositoryName)

  Map<String, String> previousApps = extractApplications(previousPlatformDescriptor)
  Map<String, String> currentApps = extractApplications(currentPlatformDescriptor)

  echo "Previous platform has ${previousApps.size()} applications"
  echo "Current platform has ${currentApps.size()} applications"

  Map<String, Map> changelog = [:]

  currentApps.each { appName, currentAppId ->
    String previousAppId = previousApps[appName]

    if (!previousAppId) {
      echo "New application detected: ${appName} (${currentAppId})"
      changelog[appName] = [
        status: 'added',
        previous: null,
        current: currentAppId,
        modules: [],
        uiModules: []
      ]
    } else if (previousAppId != currentAppId) {
      echo "Application updated: ${appName} (${previousAppId} -> ${currentAppId})"

      try {
        Map previousAppDescriptor = far.getApplicationDescriptor(previousAppId, true)
        Map currentAppDescriptor = far.getApplicationDescriptor(currentAppId, true)

        List<Map> moduleDiffs = compareModules(previousAppDescriptor.modules, currentAppDescriptor.modules)
        List<Map> uiModuleDiffs = compareModules(previousAppDescriptor.uiModules, currentAppDescriptor.uiModules)

        changelog[appName] = [
          status: 'updated',
          previous: previousAppId,
          current: currentAppId,
          modules: moduleDiffs,
          uiModules: uiModuleDiffs
        ]
      } catch (Exception e) {
        echo "Error processing application ${appName}: ${e.getMessage()}"
        changelog[appName] = [
          status: 'error',
          previous: previousAppId,
          current: currentAppId,
          error: e.getMessage(),
          modules: [],
          uiModules: []
        ]
      }
    }
  }

  previousApps.each { appName, previousAppId ->
    if (!currentApps.containsKey(appName)) {
      echo "Application removed: ${appName} (${previousAppId})"
      changelog[appName] = [
        status: 'removed',
        previous: previousAppId,
        current: null,
        modules: [],
        uiModules: []
      ]
    }
  }

  echo "Changelog generated with ${changelog.size()} application changes"
  return changelog
}

Map getPlatformDescriptor(GitHubClient gitHubClient, String sha, String repositoryName) {
  try {
    Map fileContent = gitHubClient.getFileContent(sha, 'platform-descriptor.json', repositoryName)
    String content = new String(fileContent.content.decodeBase64())
    return readJSON(text: content)
  } catch (Exception e) {
    echo "Error fetching platform-descriptor.json for ${sha}: ${e.getMessage()}"
    return [applications: [required: [], optional: [], experimental: []]]
  }
}

Map<String, String> extractApplications(Map platformDescriptor) {
  Map<String, String> apps = [:]

  List allApps = (platformDescriptor.applications?.required ?: []) +
                 (platformDescriptor.applications?.optional ?: []) +
                 (platformDescriptor.applications?.experimental ?: [])

  allApps.each { app ->
    String appName = app.name
    String appId = app.id
    apps[appName] = appId
  }

  return apps
}

List<Map> compareModules(List previousModules, List currentModules) {
  List<Map> diffs = []
  Map<String, Map> previousModulesMap = [:]
  Map<String, Map> currentModulesMap = [:]

  previousModules?.each { mod ->
    previousModulesMap[mod.name] = mod
  }

  currentModules?.each { mod ->
    currentModulesMap[mod.name] = mod
  }

  currentModulesMap.each { moduleName, currentMod ->
    Map previousMod = previousModulesMap[moduleName]

    if (!previousMod) {
      diffs << [
        name: moduleName,
        status: 'added',
        previous: null,
        current: currentMod.id,
        currentVersion: currentMod.version
      ]
    } else if (previousMod.id != currentMod.id) {
      diffs << [
        name: moduleName,
        status: 'updated',
        previous: previousMod.id,
        current: currentMod.id,
        previousVersion: previousMod.version,
        currentVersion: currentMod.version
      ]
    }
  }

  previousModulesMap.each { moduleName, previousMod ->
    if (!currentModulesMap.containsKey(moduleName)) {
      diffs << [
        name: moduleName,
        status: 'removed',
        previous: previousMod.id,
        current: null,
        previousVersion: previousMod.version
      ]
    }
  }

  return diffs.sort { it.name }
}


@SuppressWarnings('GrMethodMayBeStatic')
List renderChangelogBlock(Map<String, Map> changelog) {
  List blocks = [[type: "divider"],
                 [type: "header",
                  text: [type : "plain_text",
                         text : "Changelog",
                         emoji: true]]]

  String changeLog = ''
  List<String> sortedAppNames = changelog.keySet().sort()

  for (appName in sortedAppNames) {
    Map appChange = changelog[appName]
    String appStatus = appChange.status
    String appHeader = "*${appName}*"
    String appDetails = ""

    switch (appStatus) {
      case 'added':
        appDetails = "New application: ${appChange.current}"
        break
      case 'removed':
        appDetails = "Removed: ${appChange.previous}"
        break
      case 'updated':
        appDetails = "${appChange.previous} -> ${appChange.current}"
        break
      case 'error':
        appDetails = "Error: ${appChange.error}"
        break
    }

    String appLine = ">${appHeader}\\n>${appDetails}\\n"

    if ((changeLog + appLine).length() > 2998) {
      changeLog += '...'
      break
    }

    changeLog += appLine

    if (appStatus == 'updated') {
      List<Map> allModules = (appChange.modules ?: []) + (appChange.uiModules ?: [])

      for (moduleDiff in allModules) {
        String moduleStatus = moduleDiff.status
        String moduleLine = ""

        switch (moduleStatus) {
          case 'added':
            moduleLine = ">  + ${moduleDiff.name}: ${moduleDiff.current}\\n"
            break
          case 'removed':
            moduleLine = ">  - ${moduleDiff.name}: ${moduleDiff.previous}\\n"
            break
          case 'updated':
            moduleLine = ">  ${moduleDiff.name}: ${moduleDiff.previousVersion} -> ${moduleDiff.currentVersion}\\n"
            break
        }

        if ((changeLog + moduleLine).length() > 2998) {
          changeLog += '>  ...'
          break
        }

        changeLog += moduleLine
      }
    }

    changeLog += "\\n"
  }

  blocks << [type: "section",
             text: [type: "mrkdwn",
                    text: changeLog.replace('\\n', '\n')]]

  return blocks
}

@SuppressWarnings('GrMethodMayBeStatic')
String renderChangelogSection(Map<String, Map> changelog) {
  String changeLog = ''
  List<String> sortedAppNames = changelog.keySet().sort()

  for (appName in sortedAppNames) {
    Map appChange = changelog[appName]
    String appStatus = appChange.status
    String appHeader = "*${appName}*"
    String appDetails = ""

    switch (appStatus) {
      case 'added':
        appDetails = "New application: ${appChange.current}"
        break
      case 'removed':
        appDetails = "Removed: ${appChange.previous}"
        break
      case 'updated':
        appDetails = "${appChange.previous} -> ${appChange.current}"
        break
      case 'error':
        appDetails = "Error: ${appChange.error}"
        break
    }

    String appLine = "${appHeader}\\n${appDetails}\\n"

    if ((changeLog + appLine).length() > 2998) {
      changeLog += '>...'
      break
    }

    changeLog += appLine

    if (appStatus == 'updated') {
      List<Map> allModules = (appChange.modules ?: []) + (appChange.uiModules ?: [])

      for (moduleDiff in allModules) {
        String moduleStatus = moduleDiff.status
        String moduleLine = ""

        switch (moduleStatus) {
          case 'added':
            moduleLine = "  + ${moduleDiff.name}: ${moduleDiff.current}\\n"
            break
          case 'removed':
            moduleLine = "  - ${moduleDiff.name}: ${moduleDiff.previous}\\n"
            break
          case 'updated':
            moduleLine = "  ${moduleDiff.name}: ${moduleDiff.previousVersion} -> ${moduleDiff.currentVersion}\\n"
            break
        }

        if ((changeLog + moduleLine).length() > 2998) {
          changeLog += '>...'
          break
        }

        changeLog += moduleLine
      }
    }

    changeLog += "\\n"
  }

  String section = SlackHelper.renderSection('Changelog', changeLog.replace('"', '\\"'), '#808080', [], [])

  return section
}

String getPlainText(Map<String, Map> changelog) {
  StringBuilder plainTextBuilder = new StringBuilder()
  List<String> sortedAppNames = changelog.keySet().sort()

  sortedAppNames.each { appName ->
    Map appChange = changelog[appName]
    String appStatus = appChange.status

    switch (appStatus) {
      case 'added':
        plainTextBuilder.append("${appName}: New application (${appChange.current})\n")
        break
      case 'removed':
        plainTextBuilder.append("${appName}: Removed (${appChange.previous})\n")
        break
      case 'updated':
        plainTextBuilder.append("${appName}: ${appChange.previous} -> ${appChange.current}\n")

        List<Map> allModules = (appChange.modules ?: []) + (appChange.uiModules ?: [])
        allModules.each { moduleDiff ->
          String moduleStatus = moduleDiff.status

          switch (moduleStatus) {
            case 'added':
              plainTextBuilder.append("  + ${moduleDiff.name}: ${moduleDiff.current}\n")
              break
            case 'removed':
              plainTextBuilder.append("  - ${moduleDiff.name}: ${moduleDiff.previous}\n")
              break
            case 'updated':
              plainTextBuilder.append("  ${moduleDiff.name}: ${moduleDiff.previousVersion} -> ${moduleDiff.currentVersion}\n")
              break
          }
        }
        break
      case 'error':
        plainTextBuilder.append("${appName}: Error - ${appChange.error}\n")
        break
    }

    plainTextBuilder.append("\n")
  }

  return plainTextBuilder.toString()
}
