package org.folio.version.semantic

import org.folio.version.semantic.Order
import org.folio.version.semantic.SemanticVersionComparator
import org.folio.version.semantic.model.SemanticVersionType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SemanticVersionComparatorTest {

    @Test
    void testReleaseVersionsOrder() {
        def array = ["1.0.0", "2.0.0", "1.1.0", "2.0.1"]

        def sorted = array.toSorted(new SemanticVersionComparator(order: Order.DESC))

        Assertions.assertIterableEquals(["2.0.1", "2.0.0", "1.1.0", "1.0.0"], sorted)
    }

    @Test
    void testTypesVersionsOrder() {
        def array = ["1.0.0-branch-20210219155900", "1.0.0-20210219155800", "1.0.0"]

        def sorted = array.toSorted(new SemanticVersionComparator(order: Order.DESC))

        Assertions.assertIterableEquals(["1.0.0", "1.0.0-20210219155800", "1.0.0-branch-20210219155900"], sorted)
    }

    @Test
    void testAllVersions() {
        def array = ["1.0.7-hotfix-KW-8326-20210219155800", "1.0.6-hotfix-KW-8326-20210219155800", "1.0.6", "1.0.6-20210219155700", "1.0.6-hotfix-KW-8326-20210219155900"]

        def sorted = array.toSorted(new SemanticVersionComparator(order: Order.DESC))

        Assertions.assertIterableEquals(["1.0.7-hotfix-KW-8326-20210219155800", "1.0.6", "1.0.6-20210219155700", "1.0.6-hotfix-KW-8326-20210219155900", "1.0.6-hotfix-KW-8326-20210219155800"], sorted)
    }

    @Test
    void testPreferredBranchesMaster() {
        def array = ["1.0.7-hotfix-KW-8326-20210219155800", "1.0.6-hotfix-KW-8326-20210219155800", "1.0.6", "1.0.6-20210219155700", "1.0.6-hotfix-KW-8326-20210219155900"]

        def sorted = array.toSorted(new SemanticVersionComparator(order: Order.DESC))

        Assertions.assertIterableEquals(["1.0.7-hotfix-KW-8326-20210219155800", "1.0.6", "1.0.6-20210219155700", "1.0.6-hotfix-KW-8326-20210219155900", "1.0.6-hotfix-KW-8326-20210219155800"], sorted)
    }

    @Test
    void testPreferredBranchesDevelop() {
        def array = ["1.0.7-hotfix-KW-8326-20210219155800", "1.0.6-hotfix-KW-8326-20210219155800", "1.0.6", "1.0.6-20210219155700", "1.0.6-hotfix-KW-8326-20210219155900"]

        def sorted = array.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: ["develop"]))

        Assertions.assertIterableEquals(["1.0.6-20210219155700", "1.0.7-hotfix-KW-8326-20210219155800", "1.0.6", "1.0.6-hotfix-KW-8326-20210219155900", "1.0.6-hotfix-KW-8326-20210219155800"], sorted)
    }

    @Test
    void testPreferredBranchesBranch() {
        def array = ["1.0.7-hotfix-KW-8326-20210219155800", "1.0.6-hotfix-KW-8326-20210219155800", "1.0.6", "1.0.6-20210219155700", "1.0.6-hotfix-KW-8326-20210219155900"]

        def sorted = array.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: ["hotfix-KW-8326"]))

        Assertions.assertIterableEquals(["1.0.7-hotfix-KW-8326-20210219155800", "1.0.6-hotfix-KW-8326-20210219155900", "1.0.6-hotfix-KW-8326-20210219155800", "1.0.6", "1.0.6-20210219155700"], sorted)
    }

    @Test
    void testPreferredBranchesMasterDevelop() {
        def array = ["1.0.7-hotfix-KW-8326-20210219155800", "1.0.6-hotfix-KW-8326-20210219155800", "1.0.6", "1.0.6-20210219155700", "1.0.6-hotfix-KW-8326-20210219155900"]

        def sorted = array.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: ["master", "develop"]))

        Assertions.assertIterableEquals(["1.0.6", "1.0.6-20210219155700", "1.0.7-hotfix-KW-8326-20210219155800", "1.0.6-hotfix-KW-8326-20210219155900", "1.0.6-hotfix-KW-8326-20210219155800"], sorted)
    }

    @Test
    void testPreferredBranchesDevelopMaster() {
        def array = ["1.0.7-hotfix-KW-8326-20210219155800", "1.0.6-hotfix-KW-8326-20210219155800", "1.0.6", "1.0.6-20210219155700", "1.0.6-hotfix-KW-8326-20210219155900"]

        def sorted = array.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: ["develop", "master"]))

        Assertions.assertIterableEquals(["1.0.6-20210219155700", "1.0.6", "1.0.7-hotfix-KW-8326-20210219155800", "1.0.6-hotfix-KW-8326-20210219155900", "1.0.6-hotfix-KW-8326-20210219155800"], sorted)
    }

    @Test
    void testCheckReleaseVersions() {
        def artifacts = ["1.2.3", "1.2", "1.0.7-hotfix-KW-8326-20210219155800", "1.0.6-20210219155700"]

        def filtered = artifacts.findAll { v ->
            !(v ==~ SemanticVersionType.RELEASE.pattern)
        }

        Assertions.assertIterableEquals(["1.2", "1.0.7-hotfix-KW-8326-20210219155800", "1.0.6-20210219155700"], filtered)
    }
}
