package org.folio.version

import org.folio.client.jira.JiraClient

def execute() {
    def client = new JiraClient(this, "https://issues.folio.org", "dummy", "dummy")

    client.issueTransition("58933","Closed")
}
