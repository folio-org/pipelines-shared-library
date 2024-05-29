package org.folio

class Constants {
    //AWS
    static String AWS_REGION = "us-west-2"
    static String AWS_ADMIN_USERS = "eldiiarduishenaliev,guramjalaghonia,oleksandrhaimanov,kseniiadubniak,volodymyrkartsev,vasilikapylou,renatsafiulin,tarasspashchenko,stanislav"
    static String AWS_CREDENTIALS_ID = "stanislav_test"
    static String AWS_S3_SERVICE_ACCOUNT_ID = "ci-s3-service-account"
    static String AWS_S3_DATA_EXPORT_ID = "ci-data-export-s3"
    static String AWS_S3_POSTGRES_BACKUPS = "manage-postgres-db-backups-s3"

    //Kubecost
    static String KUBECOST_AWS_CREDENTIALS_ID = "kubecost_athena_aws_user"
    static String KUBECOST_LICENSE_KEY = "kubecost_license_key"

    //Monitoring/Prometheus
    static String SLACK_WEBHOOK_URL = "slack_webhook_url"

    //Helm
    static String HELM_MODULES_CONFIG_PATH = "resources/helm"

    static String FOLIO_ORG = "folio-org"
    static String FOLIO_GITHUB_URL = "https://github.com/folio-org"
    static String FOLIO_JIRA_URL = "https://issues.folio.org"
    static String CI_ROOT_DOMAIN = "ci.folio.org"

    static String GITHUB_CREDENTIALS_ID = "11657186-f4d4-4099-ab72-2a32e023cced"
    static String PRIVATE_GITHUB_CREDENTIALS_ID = "id-jenkins-github-personal-token-with-username"
    static String JIRA_CREDENTIALS_ID = "jenkins-jira"

    //Rancher
    static String RANCHER_TOKEN_ID = "rancher_token_v2"

    static String EBSCO_KB_CREDENTIALS_ID = "cypress_api_key_apidvcorp"

    //SMTP
    static String EMAIL_SMTP_CREDENTIALS_ID = "ses-smtp-rancher"
    static String EMAIL_SMTP_SERVER = "email-smtp.us-west-2.amazonaws.com"
    static String EMAIL_SMTP_PORT = "587"
    static String EMAIL_FROM = "noreply@ci.folio.org"

    static String NEXUS_PUBLISH_CREDENTIALS_ID = "jenkins-nexus"
    static String FOLIO_HELM_REPO_NAME = "folio-helm"
    static String FOLIO_HELM_REPO_URL = "https://folio-org.github.io/folio-helm"
    static String FOLIO_HELM_HOSTED_REPO_NAME = "helm-hosted"
    static String FOLIO_HELM_HOSTED_REPO_URL = "https://repository.folio.org/repository/helm-hosted/"
    static String FOLIO_NPM_REPO_URL = "https://repository.folio.org/repository/npm-folioci/"

    static String FOLIO_HELM_V2_REPO_URL = "https://repository.folio.org/repository/folio-helm-v2/"
    static String FOLIO_HELM_V2_REPO_NAME = "folio-helm-v2"

    // Docker
    static String DOCKER_DEV_REPOSITORY_CREDENTIALS_ID = "folio-docker-dev"
    static String DOCKER_DEV_REPOSITORY = "docker.dev.folio.org"
    static String DOCKER_FOLIO_REPOSITORY_CREDENTIALS_ID = "folio-docker-dev"
    static String DOCKER_FOLIO_REPOSITORY = "docker-folio.dev.folio.org"
    static String ECR_FOLIO_REPOSITORY = "732722833398.dkr.ecr.us-west-2.amazonaws.com"
    static String ECR_FOLIO_REPOSITORY_CREDENTIALS_ID = "aws-ecr-rw-credentials"
    static String DOCKER_K8S_CLIENT_IMAGE = "alpine/k8s:1.22.15"

    //Jenkins
    static String JENKINS_MASTER_NODE = "master"
    static String JENKINS_JOB_PROJECT = "/Rancher/Project"
    static String JENKINS_JOB_RESTORE_PG_BACKUP = "Rancher/Create-Restore-PosgreSQL-DB-backup"
    static String JENKINS_JOB_BACKEND_MODULES_DEPLOY_BRANCH = "/Rancher/Update/backend-modules-deploy-branch"

    static String TERRAFORM_DOCKER_CLIENT = "hashicorp/terraform:1.4"

    //PostgreSQL
    static String PSQL_DUMP_HELM_CHART_NAME = "psql-dump"
    static String PSQL_DUMP_HELM_INSTALL_CHART_VERSION = "1.0.3"
    static String PSQL_DUMP_BACKUPS_BUCKET_NAME = "folio-postgresql-backups"

    //Tools
    static String MAVEN_TOOL_NAME = "maven3-jenkins-slave-all"

    static String OKAPI_SUPERADMIN_CREDENTIALS_ID = "okapi-superadmin-credentials"

    //Teams
    static Map ENVS_MEMBERS_LIST = ["aggies"        : ["Aggies"],
                                    "bama"          : ["Bama"],
                                    "concorde"      : ["concorde"],
                                    "consortia"     : ["thunderjet"],
                                    "core-platform" : ["core-platform"],
                                    "cypress"       : ["AQA"],
                                    "dreamliner"    : ["Dreamliner"],
                                    "ebsco-core"    : ["ebsco-core"],
                                    "falcon"        : ["falcon"],
                                    "firebird"      : ["firebird"],
                                    "folijet"       : ["folijet"],
                                    "folijet-lotus" : ["folijet"],
                                    "karate"        : [],
                                    "metadata"      : ["spitfire"],
                                    "nla"           : ["nla"],
                                    "prokopovych"   : ["core-functional"],
                                    "scout"         : ["scout"],
                                    "spanish"       : ["unam"],
                                    "spitfire"      : ["spitfire"],
                                    "spitfire-2nd"  : ["spitfire"],
                                    "nest-es"       : ["spitfire"],
                                    "sprint"        : [],
                                    "stripes-force" : ["stripes-force"],
                                    "task-force"    : ["ebsco-core"],
                                    "task-force-2nd": ["ebsco-core"],
                                    "thor"          : ["thor"],
                                    "thunderjet"    : ["thunderjet"],
                                    "unam"          : ["unam"],
                                    "vega"          : ["vega"],
                                    "volaris"       : ["volaris"],
                                    "volaris-2nd"   : ["volaris"],
                                    "rtr"           : ["volaris"],
                                    "snapshot"      : [],
                                    "leipzig"       : ["leipzig"]]

    //Cypress
    static String CYPRESS_REPOSITORY_URL = "${FOLIO_GITHUB_URL}/stripes-testing.git"
    static String CYPRESS_ALLURE_VERSION = "2.17.2"
    static String CYPRESS_SC_URL = "https://folio-testing-sc-director.ci.folio.org"
    static String CYPRESS_SC_KEY = "secretCypressKey"
    static String CYPRESS_PROJECT = "stripes"

    //RDS
    static String BUGFEST_SNAPSHOT_DBNAME = "folio"

    static Map JMX_METRICS_AVAILABLE = ["mod-bulk-operations": "2.0.0-SNAPSHOT.71"]

    // List of modules that support R/W split
    static List READ_WRITE_MODULES = ["mod-audit",
                                      "mod-authtoken",
                                      "mod-circulation-storage",
                                      "mod-data-import",
                                      "mod-di-converter-storage",
                                      "mod-email",
                                      "mod-erm-usage",
                                      "mod-erm-usage-harvester",
                                      "mod-event-config",
                                      "mod-feesfines",
                                      "mod-invoice-storage",
                                      "mod-kb-ebsco-java",
                                      "mod-notify",
                                      "mod-oai-pmh",
                                      "mod-organization-storage",
                                      "mod-orders",
                                      "mod-orders-storage",
                                      "mod-patron-blocks",
                                      "mod-permissions",
                                      "mod-pubsub",
                                      "mod-source-record-manager",
                                      "mod-source-record-storage",
                                      "mod-template-engine",
                                      "mod-users"]

    // Data Migration Jira tickets (used for Schema Diff)
    static String DM_ISSUE_SUMMARY_PREFIX = "Schema Difference found after migration:"
    static String DM_JIRA_ISSUE_PRIORITY = "P2"
    static String DM_ISSUE_LABEL = "dataMigrationSchemaDiff"
    static String DM_JIRA_PROJECT = "FAT"
    static String DM_JIRA_ISSUE_TYPE = "Bug"
    static String DM_ISSUES_JQL = "labels = dataMigrationSchemaDiff and status != Closed"
}
