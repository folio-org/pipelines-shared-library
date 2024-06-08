package org.folio.testing.teams

class TeamAssignment {

    List<Team> teams = []

    TeamAssignment(def jsonContents) {
        jsonContents.each { entry ->
            Team team = new Team(name: entry.team, slackChannel: entry.slackChannel)
            team.getModules().addAll(entry.modules)
            teams.add(team)
        }
    }

    Map<String, Team> getTeamsByModules() {
        Map<String, Team> retVal = [:]
        teams.each {team ->
            team.modules.each {module ->
                retVal[module] = team
            }
        }
        retVal
    }

}
