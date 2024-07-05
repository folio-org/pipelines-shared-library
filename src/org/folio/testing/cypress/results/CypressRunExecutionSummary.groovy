package org.folio.testing.cypress.results

import com.cloudbees.groovy.cps.NonCPS
import org.folio.testing.IExecutionSummary
import org.folio.testing.IModuleExecutionSummary
import org.folio.testing.IRunExecutionSummary
import org.folio.testing.TestExecutionResult
import org.folio.testing.teams.Team
import org.folio.testing.teams.TeamAssignment

class CypressRunExecutionSummary implements IRunExecutionSummary, ITestParent {

  static Map<String, CypressTestExecution> tests = [:]

  Map<String, IModuleExecutionSummary> modulesExecutionSummary = [:]
  List<IExecutionSummary> children = []
  List<ITestChild> defects = []

  String uid = ""

  Map<Team, List<IModuleExecutionSummary>> getModuleResultByTeam(TeamAssignment teamAssignment) {
    Map<Team, List<IModuleExecutionSummary>> teamResults = [:]

    //TBD

    return teamResults
  }

  @Override
  int getModulesPassedCount() {
    int count = 0
    for (IModuleExecutionSummary module : modulesExecutionSummary.values()) {
      count += (module.getExecutionResult(0) == TestExecutionResult.SUCCESS) ? 1 : 0
    }

    return count
  }

  @Override
  int getModulesFailedCount() {
    int count = 0
    for (IModuleExecutionSummary module : modulesExecutionSummary.values()) {
      count += (module.getExecutionResult(0) == TestExecutionResult.FAILED) ? 1 : 0
    }

    return count
  }

  @Override
  int getModulesTotalCount() {
    return modulesExecutionSummary.size()
  }

  @Override
  int getModulesPassRate() {
    def passRateInDecimal = getModulesTotalCount() > 0 ? (getModulesPassedCount() * 100) / getModulesTotalCount() : 0
    return passRateInDecimal.intValue()
  }

  @Override
  int getPassedCount() {
    int count = 0
    for (IExecutionSummary child : children) {
      count += child.getPassedCount()
    }

    return count
  }

  @Override
  int getFailedCount() {
    int count = 0
    for (IExecutionSummary child : children) {
      count += child.getFailedCount()
    }

    return count
  }

  @Override
  int getSkippedCount() {
    int count = 0
    for (IExecutionSummary child : children) {
      count += child.getSkippedCount()
    }

    return count
  }

  @Override
  int getBrokenCount() {
    int count = 0
    for (IExecutionSummary child : children) {
      count += child.getBrokenCount()
    }

    return count
  }

  @Override
  int getTotalCount() {
    return getPassedCount() + getFailedCount() + getSkippedCount() + getBrokenCount()
  }

  @Override
  int getPassRate() {
    def passRateInDecimal = getTotalCount() > 0 ? (getPassedCount() * 100) / getTotalCount() : 0
    return passRateInDecimal.intValue()
  }

  @Override
  TestExecutionResult getExecutionResult(int passRateLimit = 50) {
    return TestExecutionResult.byPassRate(this, passRateLimit)
  }

  void addChild(IExecutionSummary child){
    if(!children.contains(child))
      children.add(child)
  }

  void addDefect(ITestChild defect){
    if(!defects.contains(defect))
      defects.add(defect)
  }

  void addDefectsFromJSON(def json){
    if(!json?.children)
      return

    defects = json?.children ? addDefectChildrenFromJSON(json?.children, this) : []
  }

  List<ITestChild> addDefectChildrenFromJSON(def json, ITestParent parent){
    List<ITestChild> ret = []

    json.each { child ->
      if (child?.children)
        ret.add(CypressExecutionDefect.addFromJSON(this, child, parent))
      else {
        CypressTestExecution test = tests.get(child?.uid)

        if(test) {
          test.defect = parent as CypressExecutionDefect
          ret.add(test)
        }
      }
    }

    return ret
  }

  @NonCPS
  @Override
  String toString() {
    return """{
      class_name: 'CypressRunExecutionSummary',
      uid: '${uid}',
      children: ${children},
      defects: ${defects},
    }"""
  }

  @Override
  boolean equals(Object obj){
    if(getClass() != obj.getClass())
      return super.equals(obj)

    return same((obj as CypressRunExecutionSummary).uid)
  }

  @Override
  boolean same(String uid) {
    return !getUid().isEmpty() && getUid().trim() == uid.trim()
  }

  static CypressRunExecutionSummary addFromJSON(def json){
    CypressRunExecutionSummary ret = new CypressRunExecutionSummary(
      uid: json?.uid,
    )

    ret.children = json?.children ? addChildrenFromJSON(json?.children, ret) : []

    return ret
  }

  static List<IExecutionSummary> addChildrenFromJSON(def json, ITestParent parent) {
    List<IExecutionSummary> ret = []

    json.each { child ->
      if (child?.children)
        ret.add(CypressSuiteExecutionSummary.addFromJSON(child, parent))
      else {
        CypressTestExecution test = CypressTestExecution.addFromJSON(child, parent)
        ret.add(test)
        tests.put(test.getUid(), test)
      }
    }

    return ret
  }
}
