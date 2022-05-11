package org.folio.utilities

import com.cloudbees.groovy.cps.NonCPS

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
        steps.error(message)
    }
}
