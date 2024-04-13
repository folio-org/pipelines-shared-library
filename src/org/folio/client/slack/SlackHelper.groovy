package org.folio.client.slack

final class SlackHelper {

  private SlackHelper() {}

  static String renderAction(String url, String text){
    return fillTemplate(['AC_TEXT': text, 'AC_URL': url]
      , new File("folioSlackTemplates/action").text)
  }

  static String renderActions(List<String> actions){
    return "[${actions.join(', ')}]"
  }

  static String renderSection(String title, String message, String color, List<String> actions){
    return fillTemplate(['SC_TITLE': title, 'SC_TEXT': message, 'SC_COLOR': color, 'SC_ACTIONS': renderActions(actions)]
      , new File("folioSlackTemplates/section").text)
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
