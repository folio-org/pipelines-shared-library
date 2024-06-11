/**
 * Method for comparing of two versions
 * @param previous
 * @param current
 * @return status (equal,upgrade,downgrade)
 */
static String compareVersion(String old_version, String new_version) {
  List old_list = (old_version - "-SNAPSHOT").tokenize('.') + '0'
  List new_list = (new_version - "-SNAPSHOT").tokenize('.') + '0'
  int commonIndices = Math.min(old_list.size(), new_list.size())
  for (int i = 0; i < commonIndices; ++i) {
    long a = old_list[i].isNumber() ? old_list[i].toLong() : Long.MAX_VALUE
    long b = new_list[i].isNumber() ? new_list[i].toLong() : Long.MIN_VALUE
    if (a > b) {
      return 'downgrade'
    }
    if (a < b) {
      return 'upgrade'
    }
  }
  return 'equal'
}

/**
 * Method for comparing previous and new maps of install json
 * @param oldMap
 * @param newMap
 * @return Map of objects (updateMap,disableMap,downgradeMap)
 */
def createActionMaps(Map oldMap, Map newMap) {
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
    } else {
      println "${key}:${value} disable"
      disableMap.put(key, value)
    }
  }

  return [
    updateMap   : updateMap,
    disableMap  : disableMap,
    downgradeMap: downgradeMap
  ]
}
