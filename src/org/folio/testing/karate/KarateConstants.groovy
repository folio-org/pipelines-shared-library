package org.folio.testing.karate

class KarateConstants {

    public static final String CUCUMBER_REPORT_PATTERN_START = "table[\\s\\S]*class=\"stats-table\"[\\s\\S]*Feature[\\s\\S]*"
    public static final String CUCUMBER_REPORT_PATTERN_END = "[\\s\\S]*/table"

    public static final String JIRA_PROJECT = "FAT"

    public static final String JIRA_ISSUE_TYPE = "Bug"

    public static final String JIRA_ISSUE_PRIORITY = "P2"

    public static final String ISSUE_LABEL = "karateRegressionPipeline"

    public static final String ISSUE_SUMMARY_PREFIX =  "Karate test fail:"

    public static final String ISSUE_OPEN_STATUS = "Open"

    public static final String ISSUE_IN_REVIEW_STATUS = "In Review"

    public static final String ISSUE_CLOSED_STATUS = "Closed"

    public static final String KARATE_ISSUES_JQL = "labels = karateRegressionPipeline and status != Closed"

}
