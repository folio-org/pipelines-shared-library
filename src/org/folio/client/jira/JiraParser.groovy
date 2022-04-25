package org.folio.client.jira

import org.folio.client.jira.model.JiraField
import org.folio.client.jira.model.JiraIssueCreateMeta
import org.folio.client.jira.model.JiraIssueType
import org.folio.client.jira.model.JiraIssueUpdateMeta
import org.folio.client.jira.model.JiraPriority
import org.folio.client.jira.model.JiraProject
import org.folio.client.jira.model.JiraStatus

class JiraParser {

    JiraProject parseProject(def json) {
        def retVal = new JiraProject(id: json.id, key: json.key, name: json.name, description: json.description, issueTypes: [], archived: json.archived)

        json.issueTypes.each { itJson ->
            retVal.issueTypes.add(parseIssueType(itJson))
        }

        retVal
    }

    JiraIssueType parseIssueType(def json) {
        new JiraIssueType(id: json.id, name: json.name, description: json.description, subtask: json.subtask)
    }

    JiraStatus parseJiraStatus(def json) {
        new JiraStatus(id: json.id, name: json.name, description: json.description)
    }

    JiraPriority parseJiraPriority(def json) {
        new JiraPriority(id: json.id, name: json.name, description: json.description)
    }

    JiraField parseJiraField(def json) {
        new JiraField(id: json.id, name: json.name)
    }

    JiraIssueCreateMeta parseIssueCreateMeta(def json) {
        def fields = json.projects[0].issuetypes[0].fields.collect { key, fieldJson ->
            parseFieldMeta(fieldJson)
        }

        new JiraIssueCreateMeta(projectId: json.projects[0].id, projectKey: json.projects[0].key,
            issueTypeId: json.projects[0].issuetypes[0].id, issueTypeName: json.projects[0].issuetypes[0].name,
            fields: fields)
    }

    JiraIssueUpdateMeta parseIssueUpdateMeta(def json) {
        def fields = json.fields.collect { key, fieldJson ->
            parseFieldMeta(fieldJson)
        }

        new JiraIssueUpdateMeta(fields: fields)
    }

    private JiraField parseFieldMeta(fieldJson) {
        def allowedValues = [:]
        fieldJson.allowedValues.each { value ->
            if (value.name) {
                allowedValues[value.name] = value.id
            } else {
                allowedValues[value.value] = value.id
            }
        }
        new JiraField(id: fieldJson.fieldId, name: fieldJson.name, allowedValues: allowedValues)
    }

}
