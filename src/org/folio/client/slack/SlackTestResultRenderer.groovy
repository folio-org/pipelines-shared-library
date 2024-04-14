package org.folio.client.slack

import com.cloudbees.groovy.cps.NonCPS
import org.folio.client.slack.templates.SlackTestResultTemplates
import org.folio.shared.TestResult
import org.folio.shared.TestType

enum SlackTestResultRenderer {
  KARATE_SUCCESS("good", SlackTestResultTemplates.KARATE_TEXT, SlackTestResultTemplates.KARATE_TITLE
                  , TestType.KARATE, TestResult.SUCCESS)
  , KARATE_FAILURE("#FF0000", SlackTestResultTemplates.KARATE_TEXT, SlackTestResultTemplates.KARATE_TITLE
                    , TestType.KARATE, TestResult.FAILURE)
  , CYPRESS_SUCCESS("good", SlackTestResultTemplates.CYPRESS_TEXT, SlackTestResultTemplates.CYPRESS_TITLE
                    , TestType.CYPRESS, TestResult.SUCCESS)
  , CYPRESS_FAILURE("#FF0000", SlackTestResultTemplates.CYPRESS_TEXT, SlackTestResultTemplates.CYPRESS_TITLE
                    , TestType.CYPRESS, TestResult.FAILURE)

  final String color
  final String textTemplate
  final String titleTemplate

  private final TestType baseType
  private final TestResult testResult

  private SlackTestResultRenderer(String color, String textTemplate, String titleTemplate
                                  , TestType type, TestResult result) {
    this.color = color
    this.textTemplate = textTemplate
    this.titleTemplate = titleTemplate

    this.baseType = type
    this.testResult = result
  }

  String renderSection(Map<String, String> textParams, String buildUrl, boolean useReportPortal, String rpUrl){
    String text = SlackHelper.fillTemplate(textParams, textTemplate)

    List<String> actions = [SlackHelper.renderAction(buildUrl, SlackTestResultTemplates.ACTION_TEXT)]
    if(useReportPortal) actions.add(SlackHelper.renderAction(rpUrl, SlackTestResultTemplates.REPORT_PORTAL_ACTION_TEXT))

    return SlackHelper.renderSection(titleTemplate, text, color, actions)
  }

  String renderSection(String buildName, String passedCnt, String brokenCnt, String failCnt, String passRate
                       , String buildUrl, boolean useReportPortal, String rpUrl){

    return renderSection(SlackTestResultTemplates.getTextParams(buildName, passedCnt, brokenCnt, failCnt, passRate)
                          , buildUrl, useReportPortal, rpUrl)
  }

  @NonCPS
  static SlackTestResultRenderer fromType(TestType type, TestResult result) throws Error{
    for(SlackTestResultRenderer elem: values()){
      if(elem.baseType == type && elem.testResult == result){
        return elem
      }
    }
    throw new Error("Unknown test type & test result combination")
  }
}
