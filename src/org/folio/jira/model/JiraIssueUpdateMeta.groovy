package org.folio.jira.model

class JiraIssueUpdateMeta {

    List<JiraField> fields = []

    Map<String, JiraField> getFieldsByName() {
        fields.collectEntries { [it.name, it] }
    }

}
