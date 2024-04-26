package org.folio.slack

import com.cloudbees.groovy.cps.NonCPS
import org.folio.slack.templates.SlackTeamTestResultTemplates
import org.folio.testing.teams.Team
import org.folio.testing.IModuleExecutionSummary
import org.folio.testing.TestExecutionResult
import org.folio.testing.TestType

enum SlackTeamTestResultRenderer {
  KARATE_SUCCESS("good", SlackTeamTestResultTemplates.SUCCESS_TEXT
                  , TestType.KARATE, TestExecutionResult.SUCCESS)
  , KARATE_FAILURE("#E9D502", SlackTeamTestResultTemplates.FAILED_TEXT
                    , TestType.KARATE, TestExecutionResult.FAILED)
  , CYPRESS_SUCCESS("good", SlackTeamTestResultTemplates.SUCCESS_TEXT
                    , TestType.CYPRESS, TestExecutionResult.SUCCESS)
  , CYPRESS_FAILURE("#E9D502", SlackTeamTestResultTemplates.FAILED_TEXT
                    , TestType.CYPRESS, TestExecutionResult.FAILED)

  final String color
  final String textTemplate

  private final TestType baseType
  private final TestExecutionResult testResult

  private SlackTeamTestResultRenderer(String color, String textTemplate
                                      , TestType type, TestExecutionResult result) {
    this.color = color
    this.textTemplate = textTemplate

    this.baseType = type
    this.testResult = result
  }

  String renderSection(Map<String, String> textParams, List<Map<String, String>> fieldsParams
                       , String existingIssuesUrl, String createdIssuesUrl){

    String text = SlackHelper.fillTemplate(textParams, textTemplate)

    List<String> actions =
      [
        SlackHelper.renderAction(existingIssuesUrl, SlackTeamTestResultTemplates.EXISTING_ISSUES_ACTION_TEXT)
      ]

    actions.add(SlackHelper.renderAction(createdIssuesUrl, SlackTeamTestResultTemplates.CREATED_ISSUES_ACTION_TEXT))

    List<String> fields = []
    fieldsParams.each {

      fields.add(SlackHelper.renderField(
        SlackHelper.fillTemplate(it, SlackTeamTestResultTemplates.FAILED_MODULE_FIELD_TITLE)
        , SlackHelper.fillTemplate(it, SlackTeamTestResultTemplates.FAILED_MODULE_FIELD_VALUE)
        , true
      ))
    }

    return SlackHelper.renderSection("", text, color, actions, fields)
  }

  String renderSection(String teamName, List<IModuleExecutionSummary> failedModulesResults
                       , String totalCount, String failedCount
                       , String existingIssuesUrl, String createdIssuesUrl){

    List<Map<String, String>> fieldsParams = []

    failedModulesResults.each {

      fieldsParams.add(SlackTeamTestResultTemplates.getFieldParams(
        it.getModuleName()
        , it.getFailedCount() as String
        , it.getTotalCount() as String)
      )
    }

    return renderSection(
      SlackTeamTestResultTemplates.getTextParams(teamName, failedCount, totalCount)
      , fieldsParams
      , existingIssuesUrl
      , createdIssuesUrl
    )
  }

  String renderSection(Team team, List<IModuleExecutionSummary> testResults
                       , String existingIssuesUrl, String createdIssuesUrl){

    List<IModuleExecutionSummary> failedModules = testResults.findAll({
      it.getExecutionResult(0) == TestExecutionResult.FAILED
    })

    return renderSection(team.getName()
      , failedModules
      , testResults.size() as String
      , failedModules.size() as String
      , existingIssuesUrl
      , createdIssuesUrl)
  }

  @NonCPS
  static SlackTeamTestResultRenderer fromType(TestType type, TestExecutionResult result) throws Error{
    for(SlackTeamTestResultRenderer elem: values()){
      if(elem.baseType == type && elem.testResult == result){
        return elem
      }
    }
    throw new Error("Unknown test type & test result combination")
  }
}
