package org.folio.slack.templates

class SlackMessageTemplates {

  private SlackMessageTemplates(){}

  static final SECTION = '''
{
    "title": "${SC_TITLE}",
    "text": "${SC_TEXT}",
    "fallback": "Formatted text",
    "color": "${SC_COLOR}",
    "fields": ${SC_FIELDS},
    "actions": ${SC_ACTIONS}
}
'''

  static final ACTION = '''
{
    "type": "button",
    "text": "${AC_TEXT}",
    "url": "${AC_URL}"
}
'''

  static final FIELD = '''
{
    "title": "${FL_TITLE}",
    "value": "${FL_VALUE}",
    "short": "${FL_IS_SHORT}"
}
'''

  static Map<String, String> getActionParams(String url, String text){
    [
      'AC_TEXT' : text
      , 'AC_URL': url
    ]
  }

  static Map<String, String> getFieldParams(String title, String value, boolean isShort){
    [
      'FL_TITLE' : title
      , 'FL_VALUE': value
      , 'FL_IS_SHORT': isShort.toString()
    ]
  }

  static Map<String, String> getSectionParams(String title, String message, String color, String actions, String fields){
    [
      'SC_TITLE': title
      , 'SC_TEXT': message
      , 'SC_COLOR': color
      , 'SC_FIELDS': fields
      , 'SC_ACTIONS': actions
    ]
  }
}
