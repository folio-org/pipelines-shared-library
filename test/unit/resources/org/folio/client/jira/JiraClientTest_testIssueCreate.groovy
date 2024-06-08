package org.folio.version

import org.folio.jira.JiraClient
import org.folio.jira.JiraConstants

def execute() {
    def client = new JiraClient(this, JiraConstants.URL, "dummy", "dummy")

    client.createJiraTicket "KRD", "Bug",
        [Summary           : "Test summary",
         Description       : "Description long",
         Priority          : "P1",
         Labels            : ["reviewed"],
         "Development Team": "Firebird"]
}
