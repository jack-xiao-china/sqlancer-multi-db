package sqlancer.mysql.oracle.eet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.common.oracle.eet.EETMultisetComparator;

class EETMultisetComparatorTest {

    @Test
    void compare_equalOrderIndependent() {
        List<List<String>> a = List.of(List.of("1", "a"), List.of("2", "b"));
        List<List<String>> b = List.of(List.of("2", "b"), List.of("1", "a"));
        assertTrue(EETMultisetComparator.compareResultMultisets(a, b));
    }

    @Test
    void compare_duplicatesPreserved() {
        List<List<String>> a = List.of(List.of("x"), List.of("x"));
        List<List<String>> b = List.of(List.of("x"), List.of("x"));
        assertTrue(EETMultisetComparator.compareResultMultisets(a, b));
    }

    @Test
    void compare_differentMultiplicity() {
        List<List<String>> a = List.of(List.of("x"), List.of("x"));
        List<List<String>> b = List.of(List.of("x"));
        assertFalse(EETMultisetComparator.compareResultMultisets(a, b));
    }

    @Test
    void rowKey_nullCell() {
        assertEquals("null\tb", EETMultisetComparator.rowKey(Arrays.asList(null, "b")));
    }

    @Test
    void compare_empty() {
        assertTrue(EETMultisetComparator.compareResultMultisets(List.of(), List.of()));
    }
}
