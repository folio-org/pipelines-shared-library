package org.folio.client.slack

import org.folio.client.slack.templates.SlackMessageTemplates

final class SlackHelper {

  private SlackHelper() {}

  static String renderAction(String url, String text){
    return fillTemplate(
            SlackMessageTemplates.getActionParams(url, text)
            , SlackMessageTemplates.ACTION)
  }

  static String renderActions(List<String> actions){
    return "[${actions.join(', ')}]"
  }

  static String renderSection(String title, String message, String color, List<String> actions){
    return fillTemplate(
            SlackMessageTemplates.getSectionParams(title, message, color, renderActions(actions))
            , SlackMessageTemplates.SECTION)
  }

  static String renderMessage(List<String> sections){
    return "[${sections.join(', ')}]"
  }

  static String fillTemplate(Map<String, String> params, String template){
    params.each { key, value ->
      template = template.replace("\${${key}}", value)
    }
    return template
  }
}
