package org.folio.testharness

import com.lesfurets.jenkins.unit.global.lib.SourceRetriever
import hudson.util.DirScanner
import hudson.util.FileVisitor

class LocalVarsSourceRetriever implements SourceRetriever {

    static final List<String> paths = ["./vars"]

    static final String GROOVY_EXTENSION = ".groovy"

    @Override
    List<URL> retrieve(String repository, String branch, String targetPath) throws IllegalStateException {
        def retVal = []
        paths.each { path ->
            new DirScanner.Full().scan(new File(path), new FileVisitor() {
                @Override
                void visit(File f, String relativePath) throws IOException {
                    if (f.getName().endsWith(GROOVY_EXTENSION)) {
                        retVal.add(f.toURI().toURL())
                    }
                }
            })
        }
        return retVal
    }
}
