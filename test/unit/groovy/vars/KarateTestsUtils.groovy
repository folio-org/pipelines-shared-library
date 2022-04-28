package vars

import groovy.json.JsonSlurper
import jenkins.plugins.http_request.ResponseContentSupplier
import org.folio.Constants
import org.folio.karate.results.KarateFeatureExecutionSummary
import org.folio.karate.results.KarateModuleExecutionSummary
import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.TeamAssignment
import org.folio.testharness.AbstractScriptTest
import org.folio.testharness.utils.TestUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class KarateTestsUtils extends AbstractScriptTest {

    @Test
    void testSyncJiraIssues() {
        setCredentials(Constants.JIRA_CREDENTIALS_ID, "user", "password")

        helper.registerAllowedMethod("httpRequest", [Map], { parameters ->
            def content
            if (parameters.url.contains("/search")) {
                content = getResourceContent("KarateTestUtils/searchIssuesResponse.json")
            }
            return new ResponseContentSupplier(content, 200)
        })

        Object summary = getKarateTestsExecutionSummary()

        def assignment = getEmptyScript().readJSON(text: getResourceContent("KarateTestUtils/teamAssignment.json"))
        TeamAssignment teamAssignment = new TeamAssignment(assignment)

        getClassScript().execute(summary, teamAssignment)

        Assertions.assertEquals("1.0.0", result.getProjectVersion())
        Assertions.assertEquals("1.0.0", result.getJarProjectVersion())
    }

    private Object getKarateTestsExecutionSummary() {
        def testsSummary = getResourceContent("KarateTestUtils/karateTestsSummary.json")
        def summary = new JsonSlurper().parseText(testsSummary)

        KarateTestsExecutionSummary retVal = new KarateTestsExecutionSummary()
        Map<String, KarateModuleExecutionSummary> modules = retVal.modulesExecutionSummary

        summary.modulesExecutionSummary.each { name, module ->
            KarateModuleExecutionSummary moduleSummary = new KarateModuleExecutionSummary(name)

            moduleSummary.executionResult = module.executionResult
            moduleSummary.featuresPassed = module.featuresPassed
            moduleSummary.featuresFailed = module.featuresFailed
            moduleSummary.featuresSkipped = module.featuresSkipped

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
