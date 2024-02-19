import groovy.json.JsonSlurperClassic
import org.folio.Constants
import org.folio.utilities.Logger

String prepareEcsIndices(String username, String password) {

  Map indices = ["ecs-snapshot_instance_subject_cs00000int": "folio-testing-ecs-snapshot_instance_subject_cs00000int",
                 "ecs-snapshot_instance_cs00000int"        : "folio-testing-ecs-snapshot_instance_cs00000int",
                 "ecs-snapshot_contributor_cs00000int"     : "folio-testing-ecs-snapshot_contributor_cs00000int",
                 "ecs-snapshot_authority_cs00000int"       : "folio-testing-ecs-snapshot_authority_cs00000int"]

  Logger logger = new Logger(this, 'folioEcsIndices')

  indices.each { source, destination ->
    logger.info("Source index: ${source} AND Destination index: ${destination}")
//  curl -X POST "localhost:9200/my-index-000001/_clone/cloned-my-index-000001?pretty"
    try {
      logger.warning("Deleting this index: ${destination}...")
      sh("curl -u \"${username}:${password}\" -X DELETE ${Constants.FOLIO_OPEN_SEARCH_URL}/${destination} > /dev/null 2>&1 &")
      sleep(30)
    } catch (Error es) {
      logger.error("Unable to delete index: ${destination}, error: ${es.getMessage()}")
    } finally {
      logger.info("Working on creation ${destination} index...")
      sh("curl -u \"${username}:${password}\" -X POST ${Constants.FOLIO_OPEN_SEARCH_URL}/${source}/_clone/${destination} -H \"Content-Type: application/json\" " +
        "> /dev/null 2>&1 &")
    }
  }
}
