package org.folio.karate.teams

class KarateTeam {

    String name

    Set<String> modules = []

    String slackChannel



    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        KarateTeam that = (KarateTeam) o

        if (modules != that.modules) return false
        if (name != that.name) return false
        if (slackChannel != that.slackChannel) return false

        return true
    }

    int hashCode() {
        int result
        result = (name != null ? name.hashCode() : 0)
        result = 31 * result + (modules != null ? modules.hashCode() : 0)
        result = 31 * result + (slackChannel != null ? slackChannel.hashCode() : 0)
        return result
    }

    @Override
    public String toString() {
        return "KarateTeam{" +
            "name='" + name + '\'' +
            ", modules=" + modules +
            ", slackChannel='" + slackChannel + '\'' +
            '}';
    }
}
