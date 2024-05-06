package org.folio.client.jira

import jenkins.plugins.http_request.ResponseContentSupplier
import org.folio.jira.model.JiraIssue
import org.folio.testharness.AbstractScriptTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class JiraClientTest extends AbstractScriptTest {

    @Test
    void testIssueCreate() {
        helper.registerAllowedMethod("httpRequest", [Map], { parameters ->
            def content
            if (parameters["url"].contains("issue/createmeta")) {
                content = getResourceContent("testIssueCreate/issueCreateMeta.json")
            } else if (parameters["url"].contains("editmeta")) {
                content = getResourceContent("testIssueCreate/issueEditMeta.json")
            } else {
                content = getResourceContent("testIssueCreate/createIssueResponse.json")
            }
            return new ResponseContentSupplier(content, 200)
        })

        String result = getScript().execute()

        Assertions.assertEquals("57997", result)
    }

    @Test
    void testIssueSearch() {
        helper.registerAllowedMethod("httpRequest", [Map], { parameters ->
            def content
            if (parameters["url"].contains("startAt=0")) {
                content = getResourceContent("testIssueSearch/response_0.json")
            } else if (parameters["url"].contains("startAt=10")) {
                content = getResourceContent("testIssueSearch/response_10.json")
            } else {
                content = getResourceContent("testIssueSearch/response_20.json")
            }
            return new ResponseContentSupplier(content, 200)
        })

        List<JiraIssue> result = getScript().execute()

        Assertions.assertEquals(25, result.size())
        Assertions.assertNotNull(result[0].id)
        Assertions.assertNotNull(result[0].key)
        Assertions.assertNotNull(result[0].summary)
        Assertions.assertNotNull(result[0].status)
    }

    @Test
    void testIssueTransition() {
        helper.registerAllowedMethod("httpRequest", [Map], { parameters ->
            def content = getResourceContent("testIssueTransition/transitions.json")
            return new ResponseContentSupplier(content, 200)
        })

        getScript().execute()
    }

    @Test
    void testIssueAddComment() {
        helper.registerAllowedMethod("httpRequest", [Map], { parameters ->
            return new ResponseContentSupplier("""{ "id": "746" }""", 200)
        })

        String commentId = getScript().execute()

        Assertions.assertEquals("746", commentId)
    }

}
