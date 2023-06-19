package org.folio.utilities

import com.cloudbees.groovy.cps.NonCPS
import hudson.AbortException

class Logger implements Serializable {

    private Object context

    private String className

    Logger(context, className) {
        this.context = context
        this.className = className
    }
    @NonCPS
    def debug(def message){
        context.println("\u001B[34m [${className}][DEBUG] - ${message} \u001B[0m")
    }

    @NonCPS
    def info(def message){
        context.println("\u001B[30m [${className}][INFO] - ${message} \u001B[0m")
    }

    @NonCPS
    def warning(def message){
        context.println("\u001B[33m [${className}][WARNING] - ${message} \u001B[0m")
    }

    @NonCPS
    def error(def message){
        context.println("\u001B[31m [${className}][ERROR] - ${message} \u001B[0m")
        throw new AbortException(message)
    }
}
