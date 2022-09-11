package org.folio.utilities

import com.cloudbees.groovy.cps.NonCPS
import hudson.AbortException

class Logger implements Serializable {

    Object steps

    String className

    Logger(steps, className) {
        this.steps = steps
        this.className = className
    }

    @NonCPS
    def info(def message){
        steps.println("\u001B[30m [${className}][INFO] - ${message} \u001B[0m")
    }

    @NonCPS
    def warning(def message){
        steps.println("\u001B[33m [${className}][WARNING] - ${message} \u001B[0m")
    }

    @NonCPS
    def error(def message){
        steps.println("\u001B[31m [${className}][ERROR] - ${message} \u001B[0m")
        throw new AbortException(message)
    }
}
