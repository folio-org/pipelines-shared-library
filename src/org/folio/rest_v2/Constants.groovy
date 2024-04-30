package org.folio.rest_v2

class Constants {
    public static final String OKAPI_REGISTRY = "https://folio-registry.dev.folio.org"
    public static final String CI_ROOT_DOMAIN = "ci.folio.org"
    public static final String ECR_FOLIO_REPOSITORY = "732722833398.dkr.ecr.us-west-2.amazonaws.com"

    public static final String KB_API_URL = "https://api.ebsco.io"
    public static final String KB_CUSTOMER_ID = "apidvcorp"

    public static final List OKAPI_SUPER_USER_PERMISSIONS = [
        "perms.users.assign.immutable",
        "perms.users.assign.mutable",
        "perms.users.assign.okapi",
        "perms.all",
        "okapi.all",
        "okapi.proxy.pull.modules.post",
        "login.all",
        "users.all"
    ]

    public static final Map<String,List> CONFIGURATIONS = [
        smtpConfig         : ["email_from.json.template",
                        "email_password.json.template",
                        "email_smtp_host.json.template",
                        "email_smtp_port.json.template",
                        "email_username.json.template"],
        resetPassword: ["usersbl_reset.json.template"]
    ]

    public static final Map WORLDCAT = [
        name                  : "OCLC WorldCat",
        url                   : "zcat.oclc.org/OLUCWorldCat",
        externalIdQueryMap    : "@attr 1=1211 \$identifier",
        internalIdEmbedPath   : "999ff\$i",
        createJobProfileId    : "d0ebb7b0-2f0f-11eb-adc1-0242ac120002",
        updateJobProfileId    : "91f9b8d6-d80e-4727-9783-73fb53e3c786",
        targetOptions         : [
            charset: "utf-8"
        ],
        externalIdentifierType: "439bfbae-75bc-4f74-9fc7-b2a2d47ce3ef",
        enabled               : true,
        authentication        : "100473910/PAOLF"
    ]
}
