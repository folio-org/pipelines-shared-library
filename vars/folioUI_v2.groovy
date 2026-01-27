import org.folio.jenkins.PodTemplates
import org.folio.models.OkapiTenant
import org.folio.models.RancherNamespace
import org.folio.models.TenantUi

void build(){
  PodTemplates podTemplates = new PodTemplates(this)

  podTemplates.stripesAgent {

  }
}

void deploy(RancherNamespace namespace, OkapiTenant tenant) {
  PodTemplates podTemplates = new PodTemplates(this)

  podTemplates.rancherAgent {
    stage('[UI] Deploy bundle') {
      TenantUi tenantUi = tenant.getTenantUi()
      def clusterName = namespace.getClusterName()
      def tenantId = tenantUi.getTenantId()
      def tag = tenantUi.getTag()
      folioHelm.withKubeConfig(clusterName) {
        folioHelm.deployFolioModule(namespace, 'ui-bundle', tag, false, tenantId)
      }
    }
  }
}

void buildAndDeploy(RancherNamespace namespace, OkapiTenant tenant) {
  build()
  deploy(namespace, tenant)
}
