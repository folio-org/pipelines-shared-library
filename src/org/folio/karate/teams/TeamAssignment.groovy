package org.folio.karate.teams

class TeamAssignment {

    List<KarateTeam> teams = []

    TeamAssignment(def jsonContents) {
        jsonContents.each { entry ->
            KarateTeam team = new KarateTeam(name: entry.team, slackChannel: entry.slackChannel)
            team.getModules().addAll(entry.modules)
            teams.add(team)
        }
    }

    @NonCPS
    Map<String, KarateTeam> getTeamsByModules() {
        Map<String, KarateTeam> retVal = [:]
        println "DEBUG in getTeamsByModules"
        teams.each {team ->
            println "$team team"
            team.modules.each {module ->
                println "$module module"
                retVal[module] = team
            }
        }
        retVal
    }

}
