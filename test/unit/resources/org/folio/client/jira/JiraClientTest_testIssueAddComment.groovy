package org.folio.version

import org.folio.client.jira.JiraClient

def execute() {
    def client = new JiraClient(this, JiraConstants.URL, "dummy", "dummy")

    client.addIssueComment("58933", "Comment adfkjghsdfjkh\nasdlkf;askdj sadjkfhashjkd")
}
