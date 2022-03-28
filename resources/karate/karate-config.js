function fn() {
    var config = {
        baseUrl: "${params.okapiUrl}",
        admin: {
            tenant: "${params.tenant}",
            name: ${params.adminUserName},
            password: "${adminPassword}"
        }
    }

    return config;
}
