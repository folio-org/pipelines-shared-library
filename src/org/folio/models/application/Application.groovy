package org.folio.models.application

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.module.EurekaModule

import java.util.regex.Matcher

class Application {
  private static String PATTERN = /^(.+?)-(\d+\.\d+\.\d+(?:-.+)?)$/

  String id
  String name
  String version
  String build

  Map descriptor = [:]
  List<EurekaModule> modules = []

  Application(){}

  Application(String id){
    this.id = id

    Matcher matcher = id =~ PATTERN

    if (matcher.matches()) {
      this.name = matcher.group(1)
      this.version = matcher.group(2)
      matcher.reset()
    } else {
      throw new InputMismatchException("Not able to extract application info. Application id '${this.id}' has wrong format")
    }

    this.build = extractBuild(version)
  }

  /**
   * Loads application details based on the provided descriptor.
   * This method extracts the name, version, and build from the descriptor,
   * and sets the modules list.
   *
   * This approach was taken intentionally due to Jenkins issue
   * "expected to call org.folio.models.application.Application.<init> but wound up catching
   * org.folio.models.module.EurekaModule.loadModuleDetails; see:
   * https://www.jenkins.io/doc/book/pipeline/cps-method-mismatches/"
   * Please utilize the following approach <b>new Application().withDescriptor(descriptor)</b>
   *
   * @param descriptor The application descriptor in JSON format.
   * @return This instance of Application for method chaining.
   */
  Application withDescriptor(Map descriptor) {
    this.descriptor = descriptor
    this.name = descriptor.name
    this.id = descriptor.id
    this.version = descriptor.version
    this.build = extractBuild(version)

    withModules(descriptor.modules.collect { module -> new EurekaModule().loadModuleDetails(module.id as String, "enabled") })
    return this
  }

  Application withModules(List<EurekaModule> modules) {
    this.modules = modules
    return this
  }

  Application withModulesIds(List<String> modulesIds) {
    this.modules = modulesIds.collect {id -> new EurekaModule().loadModuleDetails(id, "enabled")}
    return this
  }

  @Override
  public boolean equals(Object obj) {
    if (this.is(obj)) {
      return true
    }

    if (!(obj instanceof Application)) {
      return false
    }

    return this.id == ((Application) obj).id
  }

  @NonCPS
  static String extractBuild(String version){
    return version ==~ /^\d+\.\d+\.\d+-SNAPSHOT\.\d+$/ ? version.replaceFirst(/^\d+\.\d+\.\d+-SNAPSHOT\./, "") : ""
  }

  @Override
  String toString() {
    return """{
      "id": "$id",
      "name": "$name",
      "version": "$version",
      "build": "$build"${modules ? "\"modules\": $modules" : ""}
    }"""
  }
}
