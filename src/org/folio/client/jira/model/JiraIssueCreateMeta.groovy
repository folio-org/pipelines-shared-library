package org.folio.client.jira.model

class JiraIssueCreateMeta {

    String projectId

    String projectKey

    String issueTypeId

    String issueTypeName

    List<JiraField> fields = []

    Map<String, JiraField> getFieldsByName() {
        fields.collectEntries { [it.name, it] }
    }

}
