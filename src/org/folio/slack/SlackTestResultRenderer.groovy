package org.folio.slack

import com.cloudbees.groovy.cps.NonCPS
import org.folio.slack.templates.SlackTestResultTemplates
import org.folio.testing.IExecutionSummary
import org.folio.testing.TestExecutionResult
import org.folio.testing.TestType

enum SlackTestResultRenderer {
  KARATE_SUCCESS("good", SlackTestResultTemplates.KARATE_TEXT, SlackTestResultTemplates.KARATE_TITLE
    , TestType.KARATE, TestExecutionResult.SUCCESS)
  , KARATE_FAILURE("#FF0000", SlackTestResultTemplates.KARATE_TEXT, SlackTestResultTemplates.KARATE_TITLE
    , TestType.KARATE, TestExecutionResult.FAILED)
  , CYPRESS_SUCCESS("good", SlackTestResultTemplates.CYPRESS_TEXT, SlackTestResultTemplates.CYPRESS_TITLE
    , TestType.CYPRESS, TestExecutionResult.SUCCESS)
  , CYPRESS_FAILURE("#FF0000", SlackTestResultTemplates.CYPRESS_TEXT, SlackTestResultTemplates.CYPRESS_TITLE
    , TestType.CYPRESS, TestExecutionResult.FAILED)

  final String color
  final String textTemplate
  final String titleTemplate

  private final TestType baseType
  private final TestExecutionResult testResult

  private SlackTestResultRenderer(String color, String textTemplate, String titleTemplate
                                  , TestType type, TestExecutionResult result) {
    this.color = color
    this.textTemplate = textTemplate
    this.titleTemplate = titleTemplate

    this.baseType = type
    this.testResult = result
  }

  String renderSection(Map<String, String> textParams, String buildUrl, boolean useReportPortal, String rpUrl) {
    String text = SlackHelper.fillTemplate(textParams, textTemplate)

    List<String> actions = [SlackHelper.renderAction(buildUrl, SlackTestResultTemplates.ACTION_TEXT)]
    if (useReportPortal) actions.add(SlackHelper.renderAction(rpUrl, SlackTestResultTemplates.REPORT_PORTAL_ACTION_TEXT))

    return SlackHelper.renderSection(titleTemplate, text, color, actions, [])
  }

  String renderSection(String buildName, String passedCnt, String brokenCnt, String failCnt, String skippedCnt, String passRate
                       , String buildUrl, boolean useReportPortal, String rpUrl) {

    return renderSection(SlackTestResultTemplates.getTextParams(buildName, passedCnt, brokenCnt, failCnt, skippedCnt, passRate)
      , buildUrl, useReportPortal, rpUrl)
  }

  String renderSection(String buildName, IExecutionSummary summary
                       , String buildUrl, boolean useReportPortal, String rpUrl) {

    return renderSection(buildName
      , summary.passedCount as String
      , summary.brokenCount as String
      , summary.failedCount as String
      , summary.skippedCount as String
      , summary.passRate as String
      , buildUrl, useReportPortal, rpUrl)
  }

  @NonCPS
  static SlackTestResultRenderer fromType(TestType type, TestExecutionResult result) throws Error {
    for (SlackTestResultRenderer elem : values()) {
      if (elem.baseType == type && elem.testResult == result) {
        return elem
      }
    }
    throw new Error("Unknown test type & test result combination")
  }
}
