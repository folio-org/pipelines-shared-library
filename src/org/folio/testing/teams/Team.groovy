package org.folio.testing.teams

import com.cloudbees.groovy.cps.NonCPS

class Team {

  String name

  Set<String> modules = []

  String slackChannel

  @NonCPS
  boolean equals(o) {
    if (this.is(o)) return true
    if (getClass() != o.class) return false

    Team that = (Team) o

    if (modules != that.modules) return false
    if (name != that.name) return false
    if (slackChannel != that.slackChannel) return false

    return true
  }

  @NonCPS
  int hashCode() {
    int result
    result = (name != null ? name.hashCode() : 0)
    result = 31 * result + (modules != null ? modules.hashCode() : 0)
    result = 31 * result + (slackChannel != null ? slackChannel.hashCode() : 0)
    return result
  }

  @NonCPS
  @Override
  String toString() {
    return "KarateTeam{" +
      "name='" + name + '\'' +
      ", modules=" + modules +
      ", slackChannel='" + slackChannel + '\'' +
      '}'
  }
}
