package org.folio.utilities

class Logger implements Serializable {
    def steps
    String className
    Logger(steps, className) {
        this.steps = steps
        this.className = className
    }
    @NonCPS
    def info(String message){
        steps.println("\u001B[30m [${className}][INFO] - ${message} \u001B[0m")
    }
    @NonCPS
    def warning(String message){
        steps.println("\u001B[33m [${className}][WARNING] - ${message} \u001B[0m")
    }
    @NonCPS
    def error(String message){
        steps.error(message)
    }
}
