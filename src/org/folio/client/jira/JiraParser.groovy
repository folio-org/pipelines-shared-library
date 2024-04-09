package org.folio.client.jira

import org.folio.client.jira.model.*

final class JiraParser {

  private JiraParser() {}

  static JiraProject parseProject(def json) {
    def retVal = new JiraProject(id: json.id, key: json.key, name: json.name
                                , description: json.description, issueTypes: []
                                , archived: json.archived)

    json.issueTypes.each { itJson ->
      retVal.issueTypes.add(parseIssueType(itJson))
    }

    retVal
  }

  static JiraIssue parseIssue(def json) {
    new JiraIssue(id: json.id,
      key: json.key,
      summary: json.fields.summary,
      description: json.fields.description,
      project: json.fields.project?.name,
      fixVersions: json.fields.fixVersions?.name,
      status: json.fields.status?.name)
  }

  static JiraIssueType parseIssueType(def json) {
    new JiraIssueType(id: json.id, name: json.name, description: json.description, subtask: json.subtask)
  }

  static JiraIssueTransition parseIssueTransition(def json) {
    new JiraIssueTransition(id: json.id, name: json.name, statusId: json.to.id, statusName: json.to.name)
  }

  static JiraStatus parseStatus(def json) {
    new JiraStatus(id: json.id, name: json.name, description: json.description)
  }

  static JiraPriority parsePriority(def json) {
    new JiraPriority(id: json.id, name: json.name, description: json.description)
  }

  static JiraField parseField(def json) {
    new JiraField(id: json.id, name: json.name)
  }

  static JiraIssueCreateMeta parseIssueCreateMeta(def json, def pipeline) {
    def fields = json.projects[0].issuetypes[0].fields.collect {
      key, fieldJson -> parseFieldMeta(fieldJson, pipeline)
    }

    new JiraIssueCreateMeta(
      projectId: json.projects[0].id
      , projectKey: json.projects[0].key
      , issueTypeId: json.projects[0].issuetypes[0].id
      , issueTypeName: json.projects[0].issuetypes[0].name
      , fields: fields)
  }

  static JiraIssueUpdateMeta parseIssueUpdateMeta(def json) {
    def fields = json.fields.collect { key, fieldJson ->
      parseFieldMeta(fieldJson)
    }

    new JiraIssueUpdateMeta(fields: fields)
  }

  private static JiraField parseFieldMeta(fieldJson, def pipeline = null) {
    Map<String, String> allowedValues = [:]
    fieldJson.allowedValues.each { value ->
      if (value.name) {
        allowedValues[value.name] = value.id
      } else {
        allowedValues[value.value] = value.id
      }
    }

    pipeline.println("JiraParser.parseFieldMeta fieldJson=${fieldJson}")
    pipeline.println("JiraParser.parseFieldMeta fieldJson.fieldId=${fieldJson.fieldId} fieldJson.name=${fieldJson.name} allowedValues=${allowedValues}")
    new JiraField(id: fieldJson.fieldId, name: fieldJson.name, allowedValues: allowedValues)
  }

}
