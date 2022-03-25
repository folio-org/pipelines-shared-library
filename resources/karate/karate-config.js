function fn() {
    var config = {
        baseUrl: "${okapiUrl}",
        admin: {
            tenant: "${tenant}",
            name: ${adminUserName},
            password: "${adminPassword}"
        }
    }

    return config;
}
