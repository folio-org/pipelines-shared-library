package org.folio.client.jira.model

class JiraIssue {

    String id

    String key

    String summary

    String description

    String status

    String project

    String fixVersions

    @Override
    String toString() {
        return "JiraIssue{" +
                "id='" + (id ?: 'null') + '\'' +
                ", key='" + (key ?: 'null') + '\'' +
                ", summary='" + (summary ?: 'null') + '\'' +
                ", description='" + (description ?: 'null') + '\'' +
                ", status='" + (status ?: 'null') + '\'' +
                ", project='" + (project ?: 'null') + '\'' +
                ", fixVersions='" + (fixVersions ?: 'null') + '\'' +
                '}'
    }

}
