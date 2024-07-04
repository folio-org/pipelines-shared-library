package org.folio.testing.cypress.results

import com.cloudbees.groovy.cps.NonCPS

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
    if(getClass() != obj.getClass())
      return super.equals(obj)

    return same((obj as CypressExecutionDefect).uid)
  }

  @Override
  boolean same(String uid) {
    return !getUid().isEmpty() && getUid().trim() == uid.trim()
  }

  @NonCPS
  @Override
  String toString() {
    return """{
      class_name: 'CypressExecutionDefect',
      uid: '${uid}',
      name: '${name}',
      parent: '${parent.getUid()}',
      children: ${children},
    }"""
  }

  static CypressExecutionDefect addFromJSON(CypressRunExecutionSummary run, def json, ITestParent parent, def context=null) {
    context?.println("CypressExecutionDefect.addFromJSON json=${json}")

    if(!json?.children)
      return null

    context?.println("CypressExecutionDefect.addFromJSON Add new")

    CypressExecutionDefect ret = new CypressExecutionDefect(
      (String)(json?.name),
      (String)(json?.uid),
      parent
    )

    ret.children = run.addDefectChildrenFromJSON(json.children, ret, context)

    context?.println("CypressExecutionDefect.addFromJSON ret=${ret}")

    return ret
  }
}
