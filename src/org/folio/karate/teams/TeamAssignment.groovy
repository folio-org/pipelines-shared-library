package org.folio.karate.teams

@NonCPS
class TeamAssignment {

    List<KarateTeam> teams = []

    TeamAssignment(def jsonContents) {
        println "DEBUG in TeamAssignment"
        jsonContents.each { entry ->
            KarateTeam team = new KarateTeam(name: entry.team, slackChannel: entry.slackChannel)
            team.getModules().addAll(entry.modules)
            teams.add(team)
        }
    }

    @NonCPS
    Map<String, KarateTeam> getTeamsByModules() {
        println "DEBUG in getTeamsByModules"
        Map<String, KarateTeam> retVal = [:]
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
