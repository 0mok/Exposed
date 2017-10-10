package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun<T> assertEqualCollectionsImpl(collection : Collection<T>, expected : Collection<T>) {
    assertEquals (expected.size, collection.size, "Count mismatch on ${currentDialect.name}")
    for (p in collection) {
        assert(expected.any {p == it}) { "Unexpected element in collection pair $p on ${currentDialect.name}" }
    }
}

fun<T> assertEqualCollections (collection : Collection<T>, expected : Collection<T>) {
    assertEqualCollectionsImpl(collection, expected)
}

fun<T> assertEqualCollections (collection : Collection<T>, vararg expected : T) {
    assertEqualCollectionsImpl(collection, expected.toList())
}

fun<T> assertEqualCollections (collection : Iterable<T>, vararg expected : T) {
    assertEqualCollectionsImpl(collection.toList(), expected.toList())
}

fun<T> assertEqualCollections (collection : Iterable<T>, expected : Collection<T>) {
    assertEqualCollectionsImpl(collection.toList(), expected)
}

fun<T> assertEqualLists (l1: List<T>, l2: List<T>) {
    assertEquals(l1.size, l2.size, "Count mismatch")
    for (i in 0..l1.size -1)
        assertEquals(l1[i], l2[i], "Error at pos $i:")
}

fun<T> assertEqualLists (l1: List<T>, vararg expected : T) {
    assertEqualLists(l1, expected.toList())
}

fun assertEqualDateTime(d1: DateTime?, d2: DateTime?) {
    when{
        d1 == null && d2 == null -> return
        d1 == null && d2 != null -> error("d1 is null while d2 is not")
        d2 == null -> error ("d1 is not null while d2 is null")
        d1 == null -> error("Impossible")
        // Mysql doesn't support millis prior 5.6.4
        currentDialect == MysqlDialect && !MysqlDialect.isFractionDateTimeSupported() ->
            assertEquals(d1.millis / 1000, d2.millis / 1000)
        currentDialect == SQLServerDialect -> {
            val difference = d2.millis - d1.millis
            assertTrue(Math.abs(difference) < 3, "SQLServer should store with error not more than 2 milliseconds, but eror is ${difference}")
        }
        else -> assertEquals(d1.millis, d2.millis)
    }
}

fun equalDateTime(d1: DateTime?, d2: DateTime?) = try {
    assertEqualDateTime(d1, d2)
    true
} catch (e: Exception) {
    false
}
