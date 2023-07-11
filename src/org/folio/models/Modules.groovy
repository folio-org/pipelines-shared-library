package org.folio.models

import groovy.json.JsonSlurper

/**
 * The Modules class is responsible for managing information about modules
 * and allows operations such as setting installation JSON, removing modules,
 * and generating installation JSON from module IDs.
 */
class Modules {
    private static final String EDGE_PREFIX = "edge-"
    private static final String MOD_PREFIX = "mod-"

    List installJson
    Map allModules
    Map backendModules
    Map edgeModules
    List discoveryList

    Modules() {}

    /**
     * Sets the installation JSON from a string or a list and initializes
     * all modules, backend modules, edge modules, and discovery list.
     *
     * @param installJson the installation JSON as a string or a list.
     * @throws IllegalArgumentException if installJson is not a string or a list,
     *                                  or if installJson is null.
     */
    void setInstallJson(Object installJson) {
        if (installJson == null) {
            throw new IllegalArgumentException("installJson cannot be null")
        }
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

        this.installJson.id.each { id ->
            def match = (id =~ /^(.*)-(\d*\.\d*\.\d*.*)$/)
            if (match) {
                def (_, module_name, version) = match[0]
                this.allModules[module_name] = version
            }
        }

        this.edgeModules = this.allModules.findAll { name, version -> name.startsWith(EDGE_PREFIX) }
        this.backendModules = this.allModules.findAll { name, version -> name.startsWith(MOD_PREFIX) }
        this.backendModules.collect { name, version ->
            this.discoveryList << [srvcId: "${name}-${version}", instId: "${name}-${version}", url: "http://${name}"]
        }
    }

    /**
     * Removes a module by its name.
     *
     * @param moduleName the name of the module to be removed.
     */
    void removeModule(String moduleName) {
        this.installJson = this.installJson.findAll { it?.id?.startsWith(moduleName) != true }
        setInstallJson(this.installJson)
    }

    /**
     * Removes modules by its names.
     *
     * @param modulesNames the list of names of the modules to be removed.
     */
    void removeModules(List<String> modulesNames) {
        modulesNames.each {moduleName->
            this.installJson = this.installJson.findAll { it?.id?.startsWith(moduleName) != true }
        }
        setInstallJson(this.installJson)
    }

    /**
     * Generates a list of maps for installation JSON from a list of module IDs and an action.
     *
     * @param moduleIds the list of module IDs.
     * @param action the action to be taken.
     * @return a list of maps with the module ID and the action.
     */
    static List<Map<String, String>> generateInstallJsonFromIds(List<String> moduleIds, String action) {
        return moduleIds.collect { moduleId -> [id: moduleId, action: action] }
    }

    /**
     * Adds a new module to the installJson list.
     * The module is represented as a map with an 'id' key (set to the moduleId argument)
     * and an 'action' key (set to 'enable').
     *
     * @param moduleId the ID of the module to add
     */
    void addModule(String moduleId) {
        Map<String, String> module = [
            'id'    : moduleId,
            'action': 'enable'
        ]
        this.installJson << module
        this.setInstallJson(this.installJson)
    }

    /**
     * Adds multiple new modules to the installJson list.
     * It does this by calling the addModule method for each ID in the moduleIds argument.
     *
     * @param moduleIds the list of module IDs to add
     */
    void addModules(List<String> modulesIds) {
        modulesIds.each { moduleId ->
            Map<String, String> module = [
                'id'    : moduleId,
                'action': 'enable'
            ]
            this.installJson << module
        }
        this.setInstallJson(this.installJson)
    }
}
