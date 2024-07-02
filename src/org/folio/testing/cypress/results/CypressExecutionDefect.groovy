package org.folio.testing.cypress.results

class CypressExecutionDefect extends Object {

  List<CypressTestExecution> defectTests = []

  String name = ""
  String uid = ""
  CypressExecutionDefect parent

  CypressExecutionDefect(String name, String uid){
    this.name = name
    this.uid = uid

    CypressRunExecutionSummary.addDefect(this)
  }

  CypressExecutionDefect(String name, String uid, CypressExecutionDefect parent){
    this(name, uid)
    this.parent = parent
  }

  @Override
  boolean equals(Object obj){
    if(getClass() != obj.class)
      return super.equals(obj)

    return !uid.isEmpty() && uid == ((CypressExecutionDefect)obj).uid
  }
}
