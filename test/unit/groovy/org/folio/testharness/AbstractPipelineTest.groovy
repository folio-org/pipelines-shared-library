package org.folio.testharness

import com.lesfurets.jenkins.unit.BasePipelineTest
import com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration
import hudson.model.Item
import hudson.model.Run
import hudson.model.TaskListener
import jenkins.plugins.http_request.HttpMode
import jenkins.plugins.http_request.HttpRequestExecution
import jenkins.plugins.http_request.HttpRequestStep
import jenkins.plugins.http_request.util.HttpRequestNameValuePair
import net.sf.json.JSONSerializer
import org.jenkinsci.plugins.workflow.support.DefaultStepContext
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

abstract class AbstractPipelineTest extends BasePipelineTest {

    static String LOCAL_LIBRARY = "local"

    static String GROOVY_EXTENSION = ".groovy"

    Map<String, Map<CredentialsValue, String>> credentials = [:]

    AbstractPipelineTest() {
        helper = new CustomPipelineTestHelper()
    }

    @BeforeEach
    void setUp() {
        super.setUp()

        registerLocalLibrary()
        registerError()

        registerHttpRequest()
        registerReadJSON()
        registerReadYaml()
        registerWithCredentials()
        registerUsernamePassword()
    }

    void setCredentials(String credentials, String username, String password) {
        def values = [:]
        values[CredentialsValue.Username] = username
        values[CredentialsValue.Password] = password

        this.credentials[credentials] = values
    }

    void registerLocalLibrary() {
        def library = LibraryConfiguration.library()
            .name(LOCAL_LIBRARY)
            .retriever(new LocalLibrarySourceRetriever())
            .targetPath("dummy")
            .defaultVersion("dummy")
            .allowOverride(true)
            .implicit(false)
            .build()

        helper.registerSharedLibrary(library)
    }

    private void registerError() {
        helper.registerAllowedMethod("error", [String.class], { message ->
            throw new RuntimeException(message)
        })
    }

    private void registerHttpRequest() {
        helper.registerAllowedMethod("httpRequest", [Map], { parameters ->
            HttpRequestStep request = new HttpRequestStep((String) parameters["url"])
            def headers = []
            if (parameters["httpMode"]) {
                request.setHttpMode(HttpMode.valueOf(parameters["httpMode"]))
            }
            if (parameters["contentType"]) {
                headers.add(new HttpRequestNameValuePair("Content-Type", ((String) parameters["contentType"]).toLowerCase().replaceAll("_", "/")))
            }
            if (parameters["customHeaders"]) {
                parameters["customHeaders"].each { map ->
                    headers.add(new HttpRequestNameValuePair((String) map["name"], (String) map["value"]))
                }
            }
            request.setCustomHeaders(headers)
            if (parameters["requestBody"]) {
                request.setRequestBody(parameters["requestBody"])
            }
            if (parameters["validResponseCodes"]) {
                request.setValidResponseCodes(parameters["validResponseCodes"])
            }

            def context = Mockito.mock(DefaultStepContext)
            def execution = Mockito.spy(new HttpRequestStep.Execution(context, request))
            Mockito.doReturn(Mockito.mock(Item)).when(execution).getProject()

            HttpRequestExecution exec = HttpRequestExecution.from(request, TaskListener.NULL, execution)

            return exec.call()
        })
    }

    private void registerReadYaml() {
        helper.registerAllowedMethod("readYaml", [Map], { parameters ->
            if (parameters["file"]) {
                Iterable<Object> yaml = new Yaml(new SafeConstructor()).loadAll(new File((String) parameters["file"]).text)

                List<Object> result = new LinkedList<Object>()
                for (Object data : yaml) {
                    result.add(data)
                }

                if (result.size() == 1) {
                    return result.get(0)
                } else {
                    return result
                }
            } else {
                throw new IllegalArgumentException("Not implemented")
            }
        })
    }

    private void registerReadJSON() {
        helper.registerAllowedMethod("readJSON", [Map], { parameters ->
            if (parameters["text"]) {
                return JSONSerializer.toJSON(parameters["text"])
            } else {
                throw new IllegalArgumentException("Not implemented")
            }
        })
    }

    private void registerWithCredentials() {
        helper.registerAllowedMethod("withCredentials", [List.class, Closure.class], { list, closure ->
            list.forEach { entry ->
                if (entry instanceof Map) {
                    entry.each { variable, value ->
                        binding.setVariable(variable, value)
                    }
                } else {
                    binding.setVariable(entry, "$entry")
                }
            }
            def res = closure.call()
            list.forEach { entry ->
                if (entry instanceof Map) {
                    entry.each { variable, value ->
                        binding.setVariable(variable, null)
                    }
                } else {
                    binding.setVariable(entry, null)
                }
            }
            return res
        })
    }

    private void registerUsernamePassword() {
        helper.registerAllowedMethod("usernamePassword", [Map.class], { parameters ->
            def credentialsId = parameters["credentialsId"]
            if (credentials[credentialsId]) {
                def retVal = [:]
                retVal[parameters["usernameVariable"]] = credentials[credentialsId].get(CredentialsValue.Username)
                retVal[parameters["passwordVariable"]] = credentials[credentialsId].get(CredentialsValue.Password)
                return retVal
            } else {
                throw new IllegalArgumentException("  Not credentials with id '${credentialsId}' found")
            }
        })
    }

}
