package org.folio.version

import org.folio.jira.JiraClient
import org.folio.jira.JiraConstants

def execute() {
    def client = new JiraClient(this, JiraConstants.URL, "dummy", "dummy")

    client.issueTransition("58933","Closed")
}
