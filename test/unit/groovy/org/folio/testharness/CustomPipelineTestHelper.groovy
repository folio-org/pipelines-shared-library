package org.folio.testharness

import com.lesfurets.jenkins.unit.PipelineTestHelper

class CustomPipelineTestHelper extends PipelineTestHelper {

    protected void setGlobalVars(Binding binding) {
        super.setGlobalVars(binding)
        // Set method interceptor to library scripts
        binding.variables.values().each { e ->
            if (e instanceof Script) {
                Script script = Script.cast(e)
                script.metaClass.invokeMethod = getMethodInterceptor()
                script.metaClass.static.invokeMethod = getMethodInterceptor()
                script.metaClass.methodMissing = getMethodMissingInterceptor()
            }
        }
    }


}
