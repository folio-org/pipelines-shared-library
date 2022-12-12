import groovy.json.JsonOutput

String compareVersion(String inA, String inB){
  List verA = inA.minus("-SNAPSHOT").tokenize('.') + '0'
  List verB = inB.minus("-SNAPSHOT").tokenize('.') + '0'
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

def createActionMaps(oldMap, newMap) {
    Map updateMap = newMap
    Map disableMap = [:]
    Map downgradeMap = [:]
    oldMap.each { key, value ->
        if (newMap.containsKey(key)) {
            println "${key} version: ${value} -> ${newMap[key]} : ${compareVersion(value, newMap[key])}"
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
            println "${key}:${value} disable"
            disableMap.put(key, value)
        }
    }
    Map actionMaps = [:]
    actionMaps.updateMap = updateMap
    actionMaps.disableMap = disableMap
    actionMaps.downgradeMap = downgradeMap
    return actionMaps
}
