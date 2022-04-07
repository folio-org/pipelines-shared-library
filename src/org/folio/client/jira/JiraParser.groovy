package org.folio.client.jira

import org.folio.client.jira.model.JiraField
import org.folio.client.jira.model.JiraIssueType
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

}
