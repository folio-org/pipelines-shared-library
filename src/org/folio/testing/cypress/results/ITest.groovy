package org.folio.testing.cypress.results

interface ITest {
  String getUid()
  boolean same(String uid)
}
