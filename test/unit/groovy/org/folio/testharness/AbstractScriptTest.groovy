package org.folio.testharness

import com.sun.org.apache.xpath.internal.operations.Bool
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo

abstract class AbstractScriptTest extends AbstractPipelineTest {

    private TestInfo testInfo

    @BeforeEach
    void setUp(TestInfo testInfo) {
        this.testInfo = testInfo

        def scriptResource = "/${getTestPackage()}/${getScriptName()}"
        boolean setup = setUpScriptRoots(scriptResource)
        if (!setup) {
            def classScriptResource = "/${getTestPackage()}/${getClassScriptName()}"
            setUpScriptRoots(classScriptResource)
        }

        super.setUp()
    }

    private boolean setUpScriptRoots(String scriptResource) {
        def script = getClass().getResource(scriptResource)
        if (script) {
            def folder = new File(script.getFile()).getParentFile().getAbsolutePath()
            scriptRoots = [folder]
            return true
        } else {
            return false
        }

    }

    Script getClassScript() {
        super.loadScript(getClassScriptName())
    }

    Script getScript() {
        super.loadScript(getScriptName())
    }

    Script getEmptyScript() {
        super.loadScript(getClass().getResource("/empty_script.groovy").getFile())
    }

    String getTestPackage() {
        testInfo.getTestClass().get().getPackage().getName().replaceAll("\\.", "/")
    }

    String getClassScriptName() {
        testInfo.getTestClass().get().getSimpleName() + "_script.groovy"
    }

    String getScriptName() {
        testInfo.getTestClass().get().getSimpleName() + "_" + testInfo.getTestMethod().get().getName() + ".groovy"
    }

    String getResourceContent(String fileName) {
        return getClass().getResourceAsStream("/${getTestPackage()}/${fileName}").text
    }

}
