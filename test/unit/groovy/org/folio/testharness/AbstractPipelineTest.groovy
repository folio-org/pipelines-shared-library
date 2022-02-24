package org.folio.testharness

import com.lesfurets.jenkins.unit.BasePipelineTest
import com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration
import org.junit.jupiter.api.BeforeEach

abstract class AbstractPipelineTest extends BasePipelineTest {

    static String LOCAL_LIBRARY = "local"

    Map<String, Map<CredentialsValue, String>> credentials = [:]

    @BeforeEach
    void setUp() {
        super.setUp()

        registerLocalLibrary()
        registerError()

//        registerHttpRequest()
//        registerReadJSON()
//        registerReadYaml()
//        registerWithCredentials()
//        registerUsernamePassword()
//
//        registerInput()
//        registerSeparator()
//        registerChoice()
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
            .retriever(new LocalVarsSourceRetriever())
            .targetPath("dummy")
            .defaultVersion("dummy")
            .allowOverride(true)
            .implicit(false)
            .build()

        helper.registerSharedLibrary(library)

// add ability to use scripts from vars
//        def scripts = new LocalVarsSourceRetriever().retrieve(null, null, null)
//        scripts.each {url ->
//            def name = new File(url.file).getName()
//            def scriptName = name.substring(0, name.length() - LocalVarsSourceRetriever.GROOVY_EXTENSION.length())
//            helper.loadScript(scriptName)
//            helper.registerAllowedMethod(scriptName, [Object[]], { parameters ->
//                helper.loadScript(scriptName)
//            })
//        }
    }

    private void registerError() {
        helper.registerAllowedMethod("error", [String.class], { message ->
            throw new RuntimeException(message)
        })
    }

//    private void registerHttpRequest() {
//        helper.registerAllowedMethod("httpRequest", [Map], { parameters ->
//            HttpRequestStep request = new HttpRequestStep((String) parameters["url"])
//            if (parameters["customHeaders"]) {
//                def headers = []
//                parameters["customHeaders"].each { map ->
//                    headers.add(new HttpRequestNameValuePair((String) map["name"], (String) map["value"]))
//                }
//                request.setCustomHeaders(headers)
//            }
//
//            def execution = new HttpRequestStep.Execution()
//
//            def stepField = HttpRequestStep.Execution.getDeclaredField("step")
//            stepField.setAccessible(true)
//            stepField.set(execution, request)
//
//            def run = Mockito.mock(Run)
//            def runField = HttpRequestStep.Execution.getDeclaredField("run")
//            runField.setAccessible(true)
//            runField.set(execution, run)
//
//            HttpRequestExecution exec = HttpRequestExecution.from(request, TaskListener.NULL, execution)
//            return exec.call()
//        })
//    }
//
//    private void registerReadYaml() {
//        helper.registerAllowedMethod("readYaml", [Map], { parameters ->
//            if (parameters["file"]) {
//                Iterable<Object> yaml = new Yaml(new SafeConstructor()).loadAll(new File((String) parameters["file"]).text)
//
//                List<Object> result = new LinkedList<Object>()
//                for (Object data : yaml) {
//                    result.add(data)
//                }
//
//                if (result.size() == 1) {
//                    return result.get(0)
//                } else {
//                    return result
//                }
//            } else {
//                throw new IllegalArgumentException("Not implemented")
//            }
//        })
//    }
//
//    private void registerReadJSON() {
//        helper.registerAllowedMethod("readJSON", [Map], { parameters ->
//            if (parameters["text"]) {
//                return JSONSerializer.toJSON(parameters["text"])
//            } else {
//                throw new IllegalArgumentException("Not implemented")
//            }
//        })
//    }
//
//    private void registerWithCredentials() {
//        helper.registerAllowedMethod("withCredentials", [List.class, Closure.class], { list, closure ->
//            list.forEach { entry ->
//                if (entry instanceof Map) {
//                    entry.each { variable, value ->
//                        binding.setVariable(variable, value)
//                    }
//                } else {
//                    binding.setVariable(entry, "$entry")
//                }
//            }
//            def res = closure.call()
//            list.forEach { entry ->
//                if (entry instanceof Map) {
//                    entry.each { variable, value ->
//                        binding.setVariable(variable, null)
//                    }
//                } else {
//                    binding.setVariable(entry, null)
//                }
//            }
//            return res
//        })
//    }
//
//    private void registerUsernamePassword() {
//        helper.registerAllowedMethod("usernamePassword", [Map], { parameters ->
//            def credentialsId = parameters["credentialsId"]
//            if (credentials[credentialsId]) {
//                def retVal = [:]
//                retVal[parameters["usernameVariable"]] = credentials[credentialsId].get(CredentialsValue.Username)
//                retVal[parameters["passwordVariable"]] = credentials[credentialsId].get(CredentialsValue.Password)
//                return retVal
//            } else {
//                throw new IllegalArgumentException("  Not credentials with id '${credentialsId}' found")
//            }
//        })
//    }
//
//    private void registerInput() {
//        helper.registerAllowedMethod("input", [Map], { parameters ->
//            Map<String, String> retVal = [:]
//            List<ParameterDefinition> uiParams = (List<ParameterDefinition>) parameters["parameters"]
//
//            uiParams.each { uiParam ->
//                if (uiParam instanceof ParameterSeparatorDefinition) {
//                    // skip
//                } else if (uiParam instanceof ChoiceParameterDefinition) {
//                    def value = ((ChoiceParameterDefinition) uiParam).choices[0]
//                        .replace("[", "")
//                        .replace("]", "")
//                        .split(",")[0]
//                    retVal[uiParam.getName()] = value
//                } else {
//                    throw new IllegalArgumentException("Not implemented")
//                }
//            }
//            retVal
//        })
//    }
//
//    private void registerSeparator() {
//        helper.registerAllowedMethod("separator", [Map], { parameters ->
//            new ParameterSeparatorDefinition(parameters["name"].toString(), parameters["sectionHeader"].toString(), parameters["separatorStyle"].toString(), parameters["sectionHeaderStyle"].toString())
//        })
//    }
//
//    private void registerChoice() {
//        helper.registerAllowedMethod("choice", [Map], { parameters ->
//            if (parameters["defaultValue"]) {
//                def choices = Arrays.asList(parameters["choices"].toString().split(ChoiceParameterDefinition.CHOICES_DELIMITER))
//                new ChoiceParameterDefinition(parameters["name"].toString(), choices, parameters["defaultValue"].toString(), parameters["description"].toString())
//            } else {
//                new ChoiceParameterDefinition(parameters["name"].toString(), parameters["choices"].toString(), parameters["description"].toString())
//            }
//        })
//    }

}
