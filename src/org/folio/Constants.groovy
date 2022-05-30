package org.folio

import org.folio.rest.model.OkapiUser

class Constants {

    static String FOLIO_GITHUB_URL = "https://github.com/folio-org"
    static String FOLIO_JIRA_URL = "https://issues.folio.org"

    static String GITHUB_CREDENTIALS_ID = "11657186-f4d4-4099-ab72-2a32e023cced"
    static String PRIVATE_GITHUB_CREDENTIALS_ID = "id-jenkins-github-personal-token-with-username"
    static String JIRA_CREDENTIALS_ID = "jenkins-jira"
    static String AWS_CREDENTIALS_ID = "stanislav_test"
    static String RANCHER_TOKEN_ID = "rancher_token"
    static String AWS_S3_SERVICE_ACCOUNT_ID = "ci-s3-service-account"
    static String AWS_S3_DATA_EXPORT_ID = "ci-data-export-s3"
    static String EBSCO_KB_CREDENTIALS_ID = "cypress_api_key_apidvcorp"

    static String EMAIL_SMTP_SERVER = "email-smtp.us-west-2.amazonaws.com"
    static String EMAIL_SMTP_PORT = "587"
    static String EMAIL_FROM = "noreply@ci.folio.org"

    static String JENKINS_MASTER_NODE = "master"

    static String TERRAFORM_DOCKER_CLIENT = "hashicorp/terraform:0.15.0"

    static Map ENVS_MEMBERS_LIST = ["bama"         : ["Bama"],
                                    "concorde"     : ["concorde"],
                                    "core-platform": ["core-platform"],
                                    "cypress"      : ["cypress-env-maintenance"],
                                    "ebsco-core"   : ["ebsco-core"],
                                    "falcon"       : ["falcon"],
                                    "firebird"     : ["firebird"],
                                    "folijet"      : ["folijet"],
                                    "karate"       : [],
                                    "metadata"     : ["spitfire"],
                                    "prokopovych"  : ["core-functional"],
                                    "scout"        : ["scout"],
                                    "spanish"      : ["unam"],
                                    "spitfire"     : ["spitfire"],
                                    "sprint"       : [],
                                    "stripes-force": ["stripes-force"],
                                    "thor"         : ["thor"],
                                    "thunderjet"   : ["thunderjet"],
                                    "unam"         : ["unam"],
                                    "vega"         : ["vega"],
                                    "volaris"      : ["volaris"],
                                    "volaris-2nd"  : ["volaris"]]

}

//"karate"       : ["Bama", "concorde", "core-platform", "ebsco-core", "falcon", "firebird", "folijet", "spitfire", "core-functional", "scout", "unam", "stripes-force", "thor", "thunderjet", "vega", "volaris"],
