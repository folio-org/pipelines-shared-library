package org.folio.testing.cypress.results

class CypressExecutionDefect implements ITestParent, ITestChild {

  List<ITestChild> children = []

  String name = ""
  String uid = ""
  ITestParent parent

  CypressExecutionDefect(String name, String uid){
    this.name = name
    this.uid = uid
  }

  CypressExecutionDefect(String name, String uid, ITestParent parent){
    this(name, uid)
    this.parent = parent
  }

  @Override
  boolean equals(Object obj){
    if(getClass() != obj.class)
      return super.equals(obj)

    return !uid.isEmpty() && uid == ((CypressExecutionDefect)obj).uid
  }

  static CypressExecutionDefect addFromJSON(CypressRunExecutionSummary run, def json, ITestParent parent) {
    if(!json?.children)
      return null

    CypressExecutionDefect ret = new CypressExecutionDefect(
      (String)(json?.name),
      (String)(json?.uid),
      parent
    )

    ret.children = run.addDefectChildrenFromJSON(json.children, ret)

    return ret
  }
}
