import org.folio.Constants
import org.folio.utilities.HttpClient
import org.folio.utilities.Logger

static void prepareEcsIndices(String username, String password) {
  String body_del = ""
  HttpClient client = new HttpClient(this)
  Map indices = [
    "ecs-snapshot_instance_subject_cs00000int": "folio-testing-ecs-snapshot_instance_subject_cs00000int",
    "ecs-snapshot_instance_cs00000int"        : "folio-testing-ecs-snapshot_instance_cs00000int",
    "ecs-snapshot_contributor_cs00000int"     : "folio-testing-ecs-snapshot_contributor_cs00000int",
    "ecs-snapshot_authority_cs00000int"       : "folio-testing-ecs-snapshot_authority_cs00000int"
  ]
  ArrayList headers = [
    [name: 'Content-type', value: "application/json"],
    [name: 'username', value: "${username}"],
    [name: 'password', value: "${password}", maskValue: true]
  ]

  indices.each { source, destination ->
    println("Source index: ${source} AND Destination index: ${destination}")

    String body = """
      [{
        "source": {
        "index": "${source}"
        },
        "dest": {
        "index": "${destination}"
        }
      }]
  """
    try {
      client.deleteRequest(Constants.FOLIO_OPEN_SEARCH + "/${destination}/?pretty", body_del, headers)
    } catch (Exception es) {
      new Logger(this, 'common').warning("Unable to delete index: ${destination}, error: ${es.getMessage()}")
    } finally {
      sleep(30000)
      client.postRequest(Constants.FOLIO_OPEN_SEARCH + "/_reindex?pretty", body, headers)
    }
  }
}
