package org.folio.models.application

import org.folio.models.FolioInstallJson
import org.folio.models.module.EurekaModule

class ApplicationList extends ArrayList<Application> {

  ApplicationList(List<String> apps) {
    super()

    apps.each { app -> add(new Application(app)) }
  }

  ApplicationList(){ super() }

  @Override
  boolean add(Application e) {
    return !any{it.equals(e) } && super.add(e)
  }

  @Override
  void add(int index, Application element) {
    if (!any { it.equals(element) })
      super.add(index, element)
  }

  @Override
  boolean addAll(int index, Collection<? extends Application> c) {
    return super.addAll(index, c.findAll{!this.any{app -> app.equals(it) } })
  }

  @Override
  boolean addAll(Collection<? extends Application> c) {
    return super.addAll(c.findAll{!this.any{app -> app.equals(it) } })
  }

  @Override
  Application set(int index, Application element) {
    return (!any { it.equals(element) } ? super.set(index, element) : get(index)) as Application
  }

  Map<String, ApplicationList> group() {
    Map<String, ApplicationList> grouped = [:]

    this.each { app ->
        if (!grouped.containsKey(app.name)) {
          grouped[app.name] = new ApplicationList()
        }

        grouped[app.name].add(app)
      }

    return grouped
  }

  ApplicationList byName(String name) {
    return group().findAll { it.key == name }.values() as ApplicationList
  }

  Map<String, String> groupMaxBuild(){
    return group().collectEntries { appName, appList ->
      [(appName): appList.max { a, b -> a.build <=> b.build }.build]
    } as Map<String, String>
  }

  String maxBuild(String name){
    return byName(name).max { a, b -> a.build <=> b.build }.build
  }

  /**
   * Retrieves a list of applications that contain a specific module name.
   *
   * @param moduleName the name of the module to search for.
   * @return a list of applications that contain the specified module name.
   */
  ApplicationList byModuleName(String moduleName) {
    return findAll { app ->
      app.modules.any { module -> module.name == moduleName }
    }
  }

  FolioInstallJson<EurekaModule> getInstallJson() {
    FolioInstallJson<EurekaModule> installJson = new FolioInstallJson(EurekaModule.class)
    this.each { app ->
      app.modules.each { module ->
        installJson.addModule(module)
      }
    }

    return installJson
  }
}
