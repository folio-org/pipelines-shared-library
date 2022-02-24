package com.altair.knowledgeworks.jenkinslibs.utils

class TestUtils {
    static String temporaryResource(Class<?> clazz, String resourcesName) {
        def pckg = clazz.getPackage().getName().replaceAll("\\.", "/")
        def is = clazz.getClassLoader().getResourceAsStream("${pckg}/${resourcesName}")

        File folder = File.createTempDir()
        File file = new File(folder, resourcesName)
        file.createNewFile()
        file.write(is.text)

        file.getAbsolutePath()
    }
}
