package org.folio.utilities

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurperClassic

class Tools {
    Object steps

    Tools(Object steps) {
        this.steps = steps
    }

    private Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    String copyResourceFileToWorkspace(String srcPath) {
        String destPath = steps.env.WORKSPACE + File.separator + new File(srcPath).getName()
        steps.writeFile file: destPath, text: steps.libraryResource(srcPath)
        logger.info("Copied ${srcPath} to ${steps.env.WORKSPACE}")
        return destPath
    }

    /**
     * Parse json object to groovy map
     * @param json
     * @return
     */
    @NonCPS
    static def jsonParse(String json) {
        new JsonSlurperClassic().parseText(json)
    }

    /**
     * Remove last char from string
     * @param str
     * @return
     */
    @NonCPS
    static def removeLastChar(String str) {
        return (str == null || str.length() == 0) ? null : (str.substring(0, str.length() - 1))
    }

    /**
     * Evaluate groovy expression
     * @param expression groovy expression
     * @param parameters parameters
     * @return result
     */
    static def eval(String expression, Map<String, Object> parameters) {
        Binding b = new Binding();
        parameters.each { k, v ->
            b.setVariable(k, v);
        }
        GroovyShell sh = new GroovyShell(b);
        return sh.evaluate(expression);
    }
}
