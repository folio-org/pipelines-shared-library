package org.folio.testing.cypress.results

import com.cloudbees.groovy.cps.NonCPS
import org.folio.testing.IExecutionSummary
import org.folio.testing.IModuleExecutionSummary
import org.folio.testing.IRunExecutionSummary
import org.folio.testing.TestExecutionResult
import org.folio.testing.teams.Team
import org.folio.testing.teams.TeamAssignment

class CypressRunExecutionSummary implements IRunExecutionSummary, ITestParent {

  Map<String, IModuleExecutionSummary> modulesExecutionSummary = [:]
  List<IExecutionSummary> children = []
  List<ITestChild> defects = []
  Map<String, CypressTestExecution> tests = [:]

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

  void addDefectsFromJSON(def json, def context=null){
    if(!json?.children)
      return

    defects = json?.children ? addDefectChildrenFromJSON(json?.children, this, context) : []
  }

  List<ITestChild> addDefectChildrenFromJSON(def json, ITestParent parent, def context=null){
    List<ITestChild> ret = []

    context?.println("CypressRunExecutionSummary.addDefectChildrenFromJSON I'm in. json=${json}")

    json.each { child ->
      context?.println("CypressRunExecutionSummary.addDefectChildrenFromJSON child=${child}")

      if (child?.children)
        ret.add(CypressExecutionDefect.addFromJSON(this, child, parent, context))
      else {
        CypressTestExecution test = tests.get(json?.uid)

        context?.println("CypressRunExecutionSummary.addDefectChildrenFromJSON test=${test}")
        context?.println("CypressRunExecutionSummary.addDefectChildrenFromJSON parent.getClass() == CypressExecutionDefect.class=${parent.getClass() == CypressExecutionDefect.class}")

        context?.println("CypressRunExecutionSummary.addDefectChildrenFromJSON test.defect=${test.defect}")

        if(test) {
          test.defect = parent as CypressExecutionDefect
          ret.add(test)
        }

        context?.println("CypressRunExecutionSummary.addDefectChildrenFromJSON test=${test}")

        context?.println("CypressRunExecutionSummary.addDefectChildrenFromJSON test.defect=${test.defect}")
      }

      context?.println("CypressRunExecutionSummary.addDefectChildrenFromJSON ret=${ret}")
    }

    return ret
  }

//  CypressTestExecution getChildById(def json, ITestParent parent, def context=null){
//    context?.println("CypressRunExecutionSummary.getChildById I'm in. json=${json}")
//    context?.println("CypressRunExecutionSummary.getChildById parent=${parent}")
//    context?.println("CypressRunExecutionSummary.getChildById parent.getChildren()=${parent.getChildren()}")
//
//    for(ITestChild child in parent.getChildren()){
//      ITestChild checkChild = child
//
//      context?.println("CypressRunExecutionSummary.getChildById child=${child}")
//      context?.println("CypressRunExecutionSummary.getChildById child.getClass()=${child.getClass()}")
//      context?.println("CypressRunExecutionSummary.getChildById child instanceof ITestParent=${child instanceof ITestParent}")
//
//      if (child instanceof ITestParent)
//        checkChild = getChildById(json, child as ITestParent, context)
//
//      context?.println("CypressRunExecutionSummary.getChildById AFTER child instanceof ITestParent checkChild=${checkChild}")
//
//      context?.println("CypressRunExecutionSummary.getChildById json?.uid=${json?.uid}")
//      context?.println("CypressRunExecutionSummary.getChildById checkChild.getUID()=${checkChild?.getUid()}")
//      context?.println("CypressRunExecutionSummary.getChildById checkChild.same(json?.uid)=${checkChild?.same(json?.uid)}")
//
//      context?.println("CypressRunExecutionSummary.getChildById checkChild.getClass()=${checkChild?.getClass()}")
//      context?.println("CypressRunExecutionSummary.getChildById checkChild.getClass() == CypressTestExecution.class=${checkChild?.getClass() == CypressTestExecution.class}")
//
//      if (checkChild && checkChild.getClass() == CypressTestExecution.class && checkChild.same(json?.uid))
//        return checkChild as CypressTestExecution
//    }
//
//    return null
//  }

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

  static CypressRunExecutionSummary addFromJSON(def json, def context){
    CypressRunExecutionSummary ret = new CypressRunExecutionSummary(
      uid: json?.uid,
    )

    context.println("CypressRunExecutionSummary.addFromJSON json=${json}")

    ret.children = json?.children ? addChildrenFromJSON(json?.children, ret, context) : []

    context.println("CypressRunExecutionSummary.addFromJSON json?.children=${json?.children ? 'Y' : 'N'}")
    context.println("CypressRunExecutionSummary.addFromJSON ret.children=${ret.children}")

    return ret
  }

  static List<IExecutionSummary> addChildrenFromJSON(def json, ITestParent parent, def context=null) {
    List<IExecutionSummary> ret = []

    context?.println("CypressRunExecutionSummary.addChildrenFromJSON I'm in. json=${json}")

    json.each { child ->
      context?.println("CypressRunExecutionSummary.addChildrenFromJSON child=${child}")

      if (child?.children)
        ret.add(CypressSuiteExecutionSummary.addFromJSON(child, parent))
      else {
        CypressTestExecution test = CypressTestExecution.addFromJSON(child, parent)
        ret.add(test)
        tests.put(test.getUid(), test)
      }

      context?.println("CypressRunExecutionSummary.addChildrenFromJSON ret=${ret}")
      context?.println("CypressRunExecutionSummary.addChildrenFromJSON tests=${tests}")
    }

    return ret
  }
}
