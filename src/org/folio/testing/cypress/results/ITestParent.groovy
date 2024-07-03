package org.folio.testing.cypress.results

import org.folio.testing.IExecutionSummary

interface ITestParent {
  List<ITestChild> getChildren()
  String getUid()
}
