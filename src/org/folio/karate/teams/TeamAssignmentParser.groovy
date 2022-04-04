package org.folio.karate.teams

class TeamAssignmentParser {

    List<KarateTeam> teams = []

    TeamAssignmentParser(def jsonContents) {
        jsonContents.each { entry ->
            KarateTeam team = new KarateTeam(name: entry.team, slackChannel: entry.slackChannel)
            team.getModules().addAll(entry.modules)
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
