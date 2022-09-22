package org.folio

import org.folio.rest.model.OkapiUser

class Constants {

    static String AWS_REGION = "us-west-2"
    static String AWS_ADMIN_USERS = "oleksandrhaimanov,kdubniak,volodymyrkartsev,vasilikapylou,tarasspashchenko,stanislav"
    static String AWS_CREDENTIALS_ID = "stanislav_test"
    static String AWS_S3_SERVICE_ACCOUNT_ID = "ci-s3-service-account"
    static String AWS_S3_DATA_EXPORT_ID = "ci-data-export-s3"
    static String AWS_S3_POSTGRES_BACKUPS = "manage-postgres-db-backups-s3"

    static String HELM_MODULES_CONFIG_PATH = "resources/helm"

    static String FOLIO_ORG = "folio-org"
    static String FOLIO_GITHUB_URL = "https://github.com/folio-org"
    static String FOLIO_JIRA_URL = "https://issues.folio.org"
    static String CI_ROOT_DOMAIN = "ci.folio.org"

    static String GITHUB_CREDENTIALS_ID = "11657186-f4d4-4099-ab72-2a32e023cced"
    static String PRIVATE_GITHUB_CREDENTIALS_ID = "id-jenkins-github-personal-token-with-username"
    static String JIRA_CREDENTIALS_ID = "jenkins-jira"

    static String RANCHER_TOKEN_ID = "rancher_token_v2"

    static String EBSCO_KB_CREDENTIALS_ID = "cypress_api_key_apidvcorp"

    static String EMAIL_SMTP_SERVER = "email-smtp.us-west-2.amazonaws.com"
    static String EMAIL_SMTP_PORT = "587"
    static String EMAIL_FROM = "noreply@ci.folio.org"

    static String NEXUS_PUBLISH_CREDENTIALS_ID = "jenkins-nexus"
    static String FOLIO_HELM_REPO_NAME = "folio-helm"
    static String FOLIO_HELM_REPO_URL = "https://folio-org.github.io/folio-helm"
    static String FOLIO_HELM_HOSTED_REPO_NAME = "helm-hosted"
    static String FOLIO_HELM_HOSTED_REPO_URL = "https://repository.folio.org/repository/helm-hosted/"
    static String FOLIO_NPM_REPO_URL = "https://repository.folio.org/repository/npm-folioci/"

    static String DOCKER_DEV_REPOSITORY_CREDENTIALS_ID = "folio-docker-dev"
    static String DOCKER_DEV_REPOSITORY = "docker.dev.folio.org"
    static String DOCKER_FOLIO_REPOSITORY_CREDENTIALS_ID = "folio-docker-dev"
    static String DOCKER_FOLIO_REPOSITORY = "docker-folio.dev.folio.org"
    static String ECR_FOLIO_UI_REPOSITORY = "732722833398.dkr.ecr.us-west-2.amazonaws.com"
    static String ECR_FOLIO_UI_REPOSITORY_CREDENTIALS_ID = "aws-ecr-rw-credentials"
    static String DOCKER_K8S_CLIENT_IMAGE = "alpine/k8s:1.22.9"

    static String JENKINS_MASTER_NODE = "master"
    static String JENKINS_JOB_PROJECT = "/Rancher/Project"
    static String JENKINS_JOB_RESTORE_PG_BACKUP = "Rancher/Create-Restore-PosgreSQL-DB-backup"

    static String TERRAFORM_DOCKER_CLIENT = "hashicorp/terraform:0.15.0"

    static String PSQL_DUMP_HELM_CHART_NAME = "psql-dump"
    static String PSQL_DUMP_HELM_INSTALL_CHART_VERSION = "1.0.2"
    static String PSQL_DUMP_BACKUPS_BUCKET_NAME = "folio-postgresql-backups"
    static String RANCHER_CLUSTERS_DEFAULT_REGION = "us-west-2"

    static Map ENVS_MEMBERS_LIST = ["bama"         : ["Bama"],
                                    "concorde"     : ["concorde"],
                                    "core-platform": ["core-platform"],
                                    "cypress"      : ["AQA"],
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
                                    "sprint-testing"       : [],
                                    "stripes-force": ["stripes-force"],
                                    "thor"         : ["thor"],
                                    "thunderjet"   : ["thunderjet"],
                                    "unam"         : ["unam"],
                                    "vega"         : ["vega"],
                                    "volaris"      : ["volaris"],
                                    "volaris-2nd"  : ["volaris"]]

}
