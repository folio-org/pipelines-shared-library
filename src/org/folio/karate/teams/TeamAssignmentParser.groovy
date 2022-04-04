package org.folio.karate.teams

class TeamAssignmentParser {

    def pipeline

    List<KarateTeam> teams = []

    TeamAssignmentParser(def pipeline, String path) {
        this.pipeline = pipeline
        def contents = pipeline.readJSON file: path

        contents.each { entry ->
            KarateTeam team = new KarateTeam(name: entry.team, slackChannel: entry.slackChannel)
            team.getModules().addAll(entry.modulesTestResult)
            teams.add(team)
        }
    }

    Map<String, KarateTeam> getTeamsByModules() {
        Map<String, KarateTeam> retVal = [:]
        teams.each {team ->
            team.modules.each {module ->
                retVal[module] = team
            }
        }
        retVal
    }

}
