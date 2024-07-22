package org.folio.testing.cypress.results

interface ITestParent extends ITest {
  List<ITestChild> getChildren()
}
