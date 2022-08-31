package org.folio.rest

import org.folio.utilities.Tools

class InstallCustomJsonsUtility {
    private Object steps

    private Tools tools = new Tools(steps)

    InstallCustomJsonsUtility(Object steps) {
        this.steps = steps
    }

    /**
     * Build discovery list of backend modules
     * @json raw json file
     * @return
     */

    List customBuildDiscoveryList(json) {
        List discoveryList = []
        tools.jsonParse(json).each {
            String version = (it['id'] =~ /\d+\.\d+\.\d+-.*\.\d+|\d+\.\d+.\d+$/).findAll()[0]
            discoveryList << [srvcId: it['id'],
                              instId: it['id'],
                              url   : 'http://' + tools.removeLastChar(it['id'] - version)]
        }
        return discoveryList
    }

    /**
     * Build enable list of backend and frontend modules
     * @param @json raw json file
     * @return
     */

    List customBuildEnableList(def json) {
        return tools.jsonParse(json)
    }
}
