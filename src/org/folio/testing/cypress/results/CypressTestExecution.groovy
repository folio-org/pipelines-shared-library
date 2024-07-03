package org.folio.testing.cypress.results

import com.cloudbees.groovy.cps.NonCPS
import org.folio.testing.IExecutionSummary
import org.folio.testing.TestExecutionResult

class CypressTestExecution implements IExecutionSummary, ITestChild {

  static enum CypressTestExecutionStatus {
    PASSED("passed"), FAILED("failed"), BROKEN("broken"), SKIPPED("skipped"), UNKNOWN(null)

    private final String status

    private CypressTestExecutionStatus(String status){
      this.status = status
    }

    @NonCPS
    static CypressTestExecutionStatus getByStatusName(String status){
      for (CypressTestExecutionStatus elem : values()) {
        if (elem.status == status.trim()) {
          return elem
        }
      }

      return UNKNOWN
    }
  }

  String name = ""
  String uid = ""
  ITestParent parent

  boolean flaky = false
  CypressTestExecutionStatus status = CypressTestExecutionStatus.UNKNOWN

  CypressExecutionDefect defect = null

  boolean newFailed = false
  boolean newPassed = false
  boolean newBroken = false
  int retriesCount = 0
  boolean retriesStatusChange = false

  CypressTestExecution(String name, String uid, ITestParent parent, CypressTestExecutionStatus status){
    this.name = name
    this.uid = uid
    this.parent = parent
    this.status = status
  }

  CypressTestExecution(String name, String uid, ITestParent parent, String status){
    this(name, uid, parent, CypressTestExecutionStatus.getByStatusName(status))
  }

  @Override
  int getPassedCount() {
    return status == CypressTestExecutionStatus.PASSED ? 1 : 0
  }

  @Override
  int getFailedCount() {
    return status == CypressTestExecutionStatus.FAILED ? 1 : 0
  }

  @Override
  int getSkippedCount() {
    return status == CypressTestExecutionStatus.SKIPPED ? 1 : 0
  }

  @Override
  int getBrokenCount() {
    return status == CypressTestExecutionStatus.BROKEN ? 1 : 0
  }

  @Override
  int getTotalCount() {
    return 1
  }

  @Override
  int getPassRate() {
    return status == CypressTestExecutionStatus.PASSED ? 100 : 0
  }

  @Override
  TestExecutionResult getExecutionResult(int passRate) {
    return status == CypressTestExecutionStatus.PASSED ? TestExecutionResult.SUCCESS : TestExecutionResult.FAILED
  }

  @Override
  boolean equals(Object obj){
    if(getClass() != obj.class)
      return super.equals(obj)

    return !uid.isEmpty() && uid == ((CypressTestExecution)obj).uid
  }

  static CypressTestExecution addFromJSON(def json, ITestParent parent) {
    if(!json?.children)
      return null

    CypressTestExecution ret = new CypressTestExecution(
      (String)(json?.name),
      (String)(json?.uid),
      parent,
      (String)(json?.status),
    )

    ret.flaky               = json?.flaky ? json?.flaky : ret.flaky
    ret.newFailed           = json?.newFailed ? json?.newFailed : ret.newFailed
    ret.newPassed           = json?.newPassed ? json?.newPassed : ret.newPassed
    ret.newBroken           = json?.newBroken ? json?.newBroken : ret.newBroken
    ret.retriesCount        = json?.retriesCount ? json?.retriesCount : ret.retriesCount
    ret.retriesStatusChange = json?.retriesStatusChange ? json?.retriesStatusChange : ret.retriesStatusChange

    return ret
  }
}
