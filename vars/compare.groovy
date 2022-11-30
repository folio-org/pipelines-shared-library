import groovy.json.JsonOutput

// def mapCurrent = '''[ {
//   "id" : "folio_developer-6.2.0",
//   "action" : "enable"
// }, {
//   "id" : "folio_handler-stripes-registry-1.2.0",
//   "action" : "enable"
// } ,{
//   "id" : "folio_myprofile-7.1.0",
//   "action" : "enable"
// }, {
//   "id" : "folio_plugin-find-erm-usage-data-provider-4.1.0",
//   "action" : "enable"
// }, {
//   "id" : "folio_plugin-find-fund-1.1.0",
//   "action" : "enable"
// }, {
//   "id" : "folio_plugin-find-package-title-4.1.0",
//   "action" : "enable"
// }]'''
// def mapNew = '''[ {
//   "id" : "folio_developer-6.2.0",
//   "action" : "enable"
// }, {
//   "id" : "folio_handler-stripes-registry-SNAPSHOT-1.3.0",
//   "action" : "enable"
// } , {
//   "id" : "folio_myprofile-7.1.0",
//   "action" : "enable"
// }, {
//   "id" : "folio_plugin-find-erm-usage-data-provider-4.1.0",
//   "action" : "enable"
// }, {
//   "id" : "folio_plugin-find-fund-1.3.0",
//   "action" : "enable"
// }, {
//   "id" : "folio_plugin-find-package-title-4.0.0",
//   "action" : "enable"
// }]'''

//def getContentAsJson (String inputMap) {
//  def slurper = new groovy.json.JsonSlurper()
//  return slurper.parseText(inputMap)
//}

def getModuleMap(String inputString) {
  def slurper = new groovy.json.JsonSlurper()
  def moduleList = slurper.parseText(inputString)
  String nameGroup = "moduleName"
  String versionGroup = "moduleVersion"
  Map moduleMap = [:]
  def patternModuleVersion = /^(?<moduleName>.*)-(?<moduleVersion>(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*).*)$/

  moduleList.each { value ->
    def matcherModule = value.id =~ patternModuleVersion
    assert matcherModule.matches()
    moduleMap[matcherModule.group(nameGroup)] = matcherModule.group(versionGroup)
  }
  return moduleMap
}

String compareVersion(String inA, String inB){
  List patSubLines = [~/-SNAPSHOT/]
//  for (patSubLine in patSubLines) {
    inA -= patSubLines
    inB -= patSubLines

  List verA = inA.tokenize('.') + '0'
  List verB = inB.tokenize('.') + '0'
  int commonIndices = Math.min(verA.size(), verB.size())
  for (int i = 0; i < commonIndices; ++i) {
    long numA = verA[i].toLong()
    long numB = verB[i].toLong()
    if (numA > numB) {
      return 'downgrade'
    }
    if (numA < numB) {
      return 'upgrade'
    }
  }
  return 'equal'
}

void createActionMaps(Map oldMap, Map newMap) {
    Map updateMap = newMap
    Map disableMap = [:]
    Map downgradeMap = [:]
    oldMap.each { key, value ->
        if (newMap.containsKey(key)) {
            switch (compareVersion(value, newMap[key])) {
            case 'equal':
                updateMap.remove(key)
                break
            case 'downgrade':
                downgradeMap.put(key, newMap[key])
                updateMap.remove(key)
                break
            default:
                break
            }
        }
        else {
            disableMap.put(key, value)
        }
    }
    Map actionMaps = [:]
    actionMaps.updateMap = updateMap
    actionMaps.disableMap = disableMap
    actionMaps.downgradeMap = downgradeMap
    return actionMaps
}

// def (Map updateMap, Map disableMap, Map downgradeMap) = createActionMaps(getModuleMap(mapCurrent), getModuleMap(mapNew)).values()
// println updateMap
// println disableMap
// println downgradeMap
