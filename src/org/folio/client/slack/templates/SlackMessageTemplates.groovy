package org.folio.client.slack.templates

class SlackMessageTemplates {

  private SlackMessageTemplates(){}

  static final SECTION = '''
{
    "title": "${SC_TITLE}",
    "text": "${SC_TEXT}",
    "fallback": "Formatted text",
    "color": "${SC_COLOR}",
    "actions": ${SC_ACTIONS}
}
'''

  static final ACTION = '''
{
    "type": "button",
    "text": "${AC_TEXT}",
    "url": "${AC_URL}",
}
'''

  static Map<String, String> getActionParams(String url, String text){
    [
      'AC_TEXT' : text
      , 'AC_URL': url
    ]
  }

  static Map<String, String> getSectionParams(String title, String message, String color, String actions){
    [
      'SC_TITLE': title
      , 'SC_TEXT': message
      , 'SC_COLOR': color
      , 'SC_ACTIONS': actions
    ]
  }
}
