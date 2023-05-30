package org.folio.models

import groovy.json.JsonSlurper

class Modules {
    List installJson
    Map allModules
    Map backendModules
    Map edgeModules
    List discoveryList

    Modules() {}

    Modules withInstallJson(Object installJson) {
        setInstallJson(installJson)
        return this
    }

    void setInstallJson(Object installJson) {
        if (installJson instanceof String) {
            this.installJson = new JsonSlurper().parseText(installJson)
        } else if (installJson instanceof List) {
            this.installJson = installJson
        } else {
            throw new IllegalArgumentException("installJson must be a JSON string or a List<Map>. Received: ${installJson.getClass()}")
        }

        this.allModules = [:]
        this.backendModules = [:]
        this.edgeModules = [:]
        this.discoveryList = []

        this.installJson.id.each {
            def (_, module_name, version) = (it =~ /^(.*)-(\d*\.\d*\.\d*.*)$/)[0]
            this.allModules[module_name] = version
        }

        this.edgeModules = this.allModules.findAll { name, version -> name.startsWith("edge-") }
        this.backendModules = this.allModules.findAll { name, version -> name.startsWith("mod-") }
        this.backendModules.collect { name, version ->
            this.discoveryList << [srvcId: "${name}-${version}",
                                   instId: "${name}-${version}",
                                   url   : "http://${name}"]
        }
    }

    void removeModule(String moduleName) {
        this.installJson = this.installJson.findAll { !it.id.startsWith(moduleName) }
        setInstallJson(this.installJson)
    }

    static List<Map<String, String>> generateInstallJsonFromIds(List<String> moduleIds, String action) {
        return moduleIds.collect { moduleId -> [id: moduleId, action: action] }
    }
}
