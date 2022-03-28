package org.folio.testharness

import com.lesfurets.jenkins.unit.global.lib.SourceRetriever
import hudson.util.DirScanner
import hudson.util.FileVisitor

class LocalLibrarySourceRetriever implements SourceRetriever {

    static final List<String> paths = ["."]

    @Override
    List<URL> retrieve(String repository, String branch, String targetPath) throws IllegalStateException {
        def retVal = []
        paths.each { path ->
            retVal.add(new File(path).toURI().toURL())
        }
        return retVal
    }
}
