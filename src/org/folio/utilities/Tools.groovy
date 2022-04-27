package org.folio.utilities

import groovy.json.JsonSlurperClassic

class Tools {
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
}
