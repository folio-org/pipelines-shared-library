import hudson.model.Action
import hudson.model.Job
import hudson.model.Run
import hudson.plugins.git.util.BuildData
import jenkins.model.Jenkins
import org.folio.models.ChangelogEntry
import org.folio.models.FolioModule
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

  List installJsonChangeLogShas = allChangeLogShas.intersect(allInstallJsonChangeLogShas)


  List updatedModulesList = []
  installJsonChangeLogShas.each { sha ->
    updatedModulesList.addAll(getUpdatedModulesList(gitHubClient.getCommitInfo(sha, platformCompleteRepositoryName)))
  }

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
      case FolioModule.ModuleType.BACKEND:
      case FolioModule.ModuleType.EDGE:
        repositoryName = module.name
        changeLogEntry.sha = getJenkinsBuildSha(repositoryName, module.buildId.toInteger())
        break
      case FolioModule.ModuleType.FRONTEND:
        repositoryName = module.name.replace('folio_', 'ui-')
        changeLogEntry.sha = gitHubClient.getWorkflowRunByNumber(repositoryName, 'build-npm.yml', module.buildId)?.head_sha ?: null
        break
    }

    Map commitInfo = [:]
    if (changeLogEntry.sha) {
      commitInfo = gitHubClient.getCommitInfo(changeLogEntry.sha, repositoryName)
    } else {
      changeLogEntry.sha = 'Unknown'
    }

    changeLogEntry.author = commitInfo?.commit?.author?.name ?: null
    changeLogEntry.commitMessage = commitInfo?.commit?.message?.split('\n', 2)?.getAt(0) ?: 'Unable to fetch commit info'
    changeLogEntry.commitLink = commitInfo?.html_url ?: null

    changeLogEntriesList << changeLogEntry
  }

  return changeLogEntriesList
}

static List getUpdatedModulesList(Map commitInfo) {
  String pattern = /(?m)-\s+"id" : "(.*?)",\n\+\s+"id" : "(.*?)",/
  Matcher matches = commitInfo['files'].find { it['filename'] == 'install.json' }['patch'] =~ pattern

  return matches.collect { match -> match[2] }
}

String getJenkinsBuildSha(String moduleName, int moduleBuildId) {
  Logger logger = new Logger(this, 'getJenkinsBuildSha')

  if (!moduleBuildId) {
    logger.warning("Module id is null or empty!")
    return null
  }

  Job moduleJob = Jenkins.instance.getItemByFullName("/folio-org/${moduleName}/master")
  if (moduleJob == null) {
    logger.warning("Job not found for module: ${moduleName}")
    return null
  }

  Run moduleBuild = moduleJob.getBuildByNumber(moduleBuildId)
  if (moduleBuild == null) {
    logger.warning("Build not found for module: ${moduleName} with Build ID: ${moduleBuildId}")
    return null
  }

  Action moduleBuildAction = moduleBuild.getActions(BuildData).find { moduleBuildAction -> moduleBuildAction.getRemoteUrls()[0] == "https://github.com/folio-org/${moduleName}.git"
  }
  if (moduleBuildAction == null) {
    logger.warning("Build data not found for module: ${moduleName}")
    return null
  }

  return moduleBuildAction.getLastBuiltRevision()?.sha1?.name ?: null
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

@NonCPS
static List<ChangelogEntry> getSortedChangeLogEntriesList(List<ChangelogEntry> toSort) {
  toSort.sort() { a, b -> a.module.id <=> b.module.id }
}
