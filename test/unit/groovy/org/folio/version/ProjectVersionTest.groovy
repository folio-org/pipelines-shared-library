package org.folio.version

import org.folio.testharness.AbstractScriptTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Project version
 */
class ProjectVersionTest extends AbstractScriptTest {

    @Test
    void testReleaseInMasterVersion() {
        ProjectVersion result = getClassScript().execute("1.0.0", "master")

        Assertions.assertEquals("1.0.0", result.getProjectVersion())
        Assertions.assertEquals("1.0.0", result.getJarProjectVersion())
    }

    @Test
    void testSnapshotInMasterVersion() {
        Throwable exception = Assertions.assertThrows(Exception.class,
            {
                getClassScript().execute("1.0.0-SNAPSHOT", "master")
            });

        Assertions.assertEquals("Version '1.0.0-SNAPSHOT' doesn't match expected 'x.x.x' format.", exception.getMessage())
    }

    @Test
    void testReleaseInDevelopVersion() {
        Throwable exception = Assertions.assertThrows(Exception.class,
            {
                getClassScript().execute("1.0.0", "develop")
            });

        Assertions.assertEquals("Version '1.0.0' doesn't match expected 'x.x.x-SNAPSHOT' format.", exception.getMessage())
    }

    @Test
    void testSnapshotInDevelopVersion() {
        ProjectVersion result = getClassScript().execute("1.0.0-SNAPSHOT", "develop")

        Assertions.assertTrue(result.getProjectVersion() ==~ "^1.0.0-\\d{14}\$")
        Assertions.assertEquals("1.0.0-SNAPSHOT", result.getJarProjectVersion())
    }

    @Test
    void testReleaseInBranchVersion() {
        Throwable exception = Assertions.assertThrows(Exception.class,
            {
                getClassScript().execute("1.0.0", "customBranch")
            });

        Assertions.assertEquals("Version '1.0.0' doesn't match expected 'x.x.x-SNAPSHOT' format.", exception.getMessage())
    }

    @Test
    void testSnapshotInBranchVersion() {
        ProjectVersion result = getClassScript().execute("1.0.0-SNAPSHOT", "customBranch")

        Assertions.assertTrue(result.getProjectVersion() ==~ "^1.0.0-customBranch-\\d{14}\$")
        Assertions.assertEquals("1.0.0-customBranch-SNAPSHOT", result.getJarProjectVersion())
    }

    @Test
    void testBranchDisabledSymbolsVersion() {
        ProjectVersion result = getClassScript().execute("1.0.0-SNAPSHOT", "bugs/PR-1234")

        Assertions.assertTrue(result.getProjectVersion() ==~ "^1.0.0-bugs-PR-1234-\\d{14}\$")
        Assertions.assertEquals("1.0.0-bugs-PR-1234-SNAPSHOT", result.getJarProjectVersion())
    }

    @Test
    void testNonSemanticInMasterVersion() {
        Throwable exception = Assertions.assertThrows(Exception.class,
            {
                getClassScript().execute("1.0", "master")
            });

        Assertions.assertEquals("Version '1.0' doesn't match expected 'x.x.x' format.", exception.getMessage())
    }

    @Test
    void testNonSemanticInBranchVersion() {
        Throwable exception = Assertions.assertThrows(Exception.class,
            {
                getClassScript().execute("1.0-SNAPSHOT", "branch")
            });

        Assertions.assertEquals("Version '1.0-SNAPSHOT' doesn't match expected 'x.x.x-SNAPSHOT' format.", exception.getMessage())
    }

}
