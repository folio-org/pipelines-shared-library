package vars


import groovy.json.JsonSlurper
import jenkins.plugins.http_request.ResponseContentSupplier
import org.folio.jira.JiraConstants
import org.folio.testing.karate.results.KarateFeatureExecutionSummary
import org.folio.testing.karate.results.KarateModuleExecutionSummary
import org.folio.testing.karate.results.KarateRunExecutionSummary
import org.folio.testing.teams.TeamAssignment
import org.folio.testharness.AbstractScriptTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class KarateTestsUtils extends AbstractScriptTest {

    class Action {
        String body
    }

    class CreateAction extends Action {}

    class AddCommentAction extends Action {}

    class TransitionAction extends Action {}

    @Test
    void testSyncJiraIssues() {

        binding.setVariable("env",
            [JOB_NAME    : "Job name",
             BUILD_NUMBER: "7",
             BUILD_URL   : "https://job.url/"]
        )
        setCredentials(JiraConstants.CREDENTIALS_ID, "user", "password")

        int createIssueId = 100000
        Map<String, List<Object>> issuesModification = [:]
        helper.registerAllowedMethod("httpRequest", [Map], { parameters ->
            def content
            if (parameters.url.contains("/search")) {
                content = getResourceContent("karateTestUtils/searchIssuesResponse.json")
            } else if (parameters.url.contains("/comment")) {
                def issueId = parameters.url.split("/")[7]
                addAction(issuesModification, issueId, new AddCommentAction(body: parameters.requestBody))
                content = """{"id":"${issueId}"}"""
            } else if (parameters["url"].contains("/transitions")) {
                if (!parameters.httpMode) {
                    content = getResourceContent("karateTestUtils/transitions.json")
                } else {
                    def issueId = parameters.url.split("/")[7]
                    addAction(issuesModification, issueId, new TransitionAction(body: parameters.requestBody))
                }
            } else if (parameters["url"].contains("issue/createmeta")) {
                content = getResourceContent("karateTestUtils/issueCreateMeta.json")
            } else if (parameters["url"].contains("editmeta")) {
                content = getResourceContent("karateTestUtils/issueEditMeta.json")
            } else {
                createIssueId++
                content = """{
                  "id": "${createIssueId}",
                  "key": "KRD-${createIssueId}",
                  "self": "${JiraConstants.ISSUE_URL}${createIssueId}"
                }"""
                addAction(issuesModification, String.valueOf(createIssueId), new CreateAction(body: parameters.requestBody))
            }
            return new ResponseContentSupplier(content, 200)
        })

        Object summary = getKarateTestsExecutionSummary()

        def assignment = getEmptyScript().readJSON(text: getResourceContent("karateTestUtils/teamAssignment.json"))
        TeamAssignment teamAssignment = new TeamAssignment(assignment)

        getClassScript().execute(summary, teamAssignment)

        Assertions.assertEquals(25, issuesModification.size())


        def openFailedIssue = issuesModification["58932"]
        Assertions.assertEquals(1, openFailedIssue.size())
        Assertions.assertTrue(openFailedIssue[0] instanceof AddCommentAction)

        def inReviewFailedIssue = issuesModification["58931"]
        Assertions.assertEquals(2, inReviewFailedIssue.size())
        Assertions.assertTrue(inReviewFailedIssue[0] instanceof AddCommentAction)
        Assertions.assertTrue(((AddCommentAction) inReviewFailedIssue[0]).body.contains("scenarios have failed for"))
        Assertions.assertTrue(inReviewFailedIssue[1] instanceof TransitionAction)
        Assertions.assertTrue(((TransitionAction) inReviewFailedIssue[1]).body.contains("81")) // Open

        def inReviewFixedIssue = issuesModification["58929"]
        Assertions.assertEquals(2, inReviewFixedIssue.size())
        Assertions.assertTrue(inReviewFixedIssue[0] instanceof AddCommentAction)
        Assertions.assertTrue(((AddCommentAction) inReviewFixedIssue[0]).body.contains("No failures"))
        Assertions.assertTrue(inReviewFixedIssue[1] instanceof TransitionAction)
        Assertions.assertTrue(((TransitionAction) inReviewFixedIssue[1]).body.contains("61")) // Closed

        def openFixedIssue = issuesModification["58909"]
        Assertions.assertEquals(2, openFixedIssue.size())
        Assertions.assertTrue(openFixedIssue[0] instanceof AddCommentAction)
        Assertions.assertTrue(((AddCommentAction) openFixedIssue[0]).body.contains("No failures"))
        Assertions.assertTrue(openFixedIssue[1] instanceof TransitionAction)
        Assertions.assertTrue(((TransitionAction) openFixedIssue[1]).body.contains("61")) // Closed

        def createIssue = issuesModification["100001"]
        Assertions.assertEquals(1, createIssue.size())
        Assertions.assertTrue(createIssue[0] instanceof CreateAction)
    }

    private void addAction(Map<String, List<Object>> issuesModification, String issueId, Action action) {
        if (!issuesModification.containsKey(issueId)) {
            issuesModification[issueId] = []
        }
        issuesModification[issueId].add(action)
    }

    private Object getKarateTestsExecutionSummary() {
        def testsSummary = getResourceContent("karateTestUtils/karateTestsSummary.json")
        def summary = new JsonSlurper().parseText(testsSummary)

        KarateRunExecutionSummary retVal = new KarateRunExecutionSummary()
        Map<String, KarateModuleExecutionSummary> modules = retVal.modulesExecutionSummary

        summary.modulesExecutionSummary.each { name, module ->
            KarateModuleExecutionSummary moduleSummary = new KarateModuleExecutionSummary(name)

            moduleSummary.executionResult = module.executionResult
            moduleSummary.featuresPassedCount = module.featuresPassedCount
            moduleSummary.featuresFailedCount = module.featuresFailedCount
            moduleSummary.featuresSkippedCount = module.featuresSkippedCount

            List<KarateFeatureExecutionSummary> featuresSummary = moduleSummary.features
            module.features.each { feature ->
                KarateFeatureExecutionSummary featureSummary = new KarateFeatureExecutionSummary()

                featureSummary.name = feature.name
                featureSummary.description = feature.description
                featureSummary.packageQualifiedName = feature.packageQualifiedName
                featureSummary.relativePath = feature.relativePath
                featureSummary.passedCount = feature.passedCount
                featureSummary.failedCount = feature.failedCount
                featureSummary.scenarioCount = feature.scenarioCount
                featureSummary.cucumberReportFile = feature.cucumberReportFile
                featureSummary.failed = feature.failed

                featuresSummary.add(featureSummary)
            }

            modules[name] = moduleSummary
        }

        retVal
    }
}
