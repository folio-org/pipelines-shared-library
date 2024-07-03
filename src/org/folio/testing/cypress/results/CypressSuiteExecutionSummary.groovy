package org.folio.testing.cypress.results

import com.cloudbees.groovy.cps.NonCPS
import org.folio.testing.IExecutionSummary
import org.folio.testing.TestExecutionResult

class CypressSuiteExecutionSummary implements IExecutionSummary, ITestParent, ITestChild{

  List<IExecutionSummary> children

  String name = ""
  String uid = ""
  ITestParent parent

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
  TestExecutionResult getExecutionResult(int passRate) {
    return TestExecutionResult.byPassRate(this, passRate)
  }

  void addChild(IExecutionSummary child){
    if(!children.contains(child))
      children.add(child)
  }

  @Override
  boolean equals(Object obj){
    if(getClass() != obj.class)
      return super.equals(obj)

    return !uid.isEmpty() && uid == ((CypressSuiteExecutionSummary)obj).uid
  }

  @NonCPS
  @Override
  String toString() {
    return """{
      class_name: 'CypressSuiteExecutionSummary',
      uid: '${uid}',
      name: '${name}',
      parent: '${parent.getUid()}',
      children: ${children},
    }"""
  }

  static CypressSuiteExecutionSummary addFromJSON(def json, ITestParent parent) {
    if(!json?.children)
      return null

    CypressSuiteExecutionSummary ret = new CypressSuiteExecutionSummary(
      name: json?.name,
      uid: json?.uid,
      parent: parent,
    )

    ret.children = CypressRunExecutionSummary.addChildrenFromJSON(json.children, ret)

    return ret
  }
}
