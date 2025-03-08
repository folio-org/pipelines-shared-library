package org.folio.utilities

import com.cloudbees.groovy.cps.NonCPS

class Logger implements Serializable {

  private Object steps

  private String className

  Logger(context, className) {
    this.steps = context
    this.className = className
  }

  @NonCPS
  def debug(def message) {
    steps.echo("\u001B[34m [${className}][DEBUG] - ${message} \u001B[0m")
  }

  @NonCPS
  def info(def message) {
    steps.echo("\u001B[30m [${className}][INFO] - ${message} \u001B[0m")
  }

  @NonCPS
  def warning(def message) {
    steps.echo("\u001B[33m [${className}][WARNING] - ${message} \u001B[0m")
  }

  @NonCPS
  def error(def message) {
    steps.echo("\u001B[31m [${className}][ERROR] - ${message} \u001B[0m")
    throw new Exception(message)
  }
}
