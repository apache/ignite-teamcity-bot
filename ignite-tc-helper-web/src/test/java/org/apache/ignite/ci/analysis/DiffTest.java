package org.apache.ignite.ci.analysis;

import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

import static java.util.Collections.emptyList;

/** */
public class DiffTest {
    /** */
    private static final Object[] emptyArray = {};

    /** */
    private final List<String> list1 = Arrays.asList("A", "C", "D", "G", "H", "I", "L");

    /** */
    private final List<String> list2 = Arrays.asList("A", "B", "C", "E", "F", "G");

    /** */
    @Test
    public void testFindDiff() {
        Diff<String> diff = new Diff<>(list1, list2);

        Assert.assertArrayEquals(new String[] {"B", "E", "F"}, diff.added().toArray());
        Assert.assertArrayEquals(new String[] {"D", "H", "I", "L"}, diff.removed().toArray());
        Assert.assertArrayEquals(new String[] {"A", "C", "G"}, diff.same().toArray());
    }

    /** */
    @Test
    public void testDuplicates() {
        Diff<String> diff =
            new Diff<>( Arrays.asList("A", "A", "B", "D", "D"), Arrays.asList("A", "B", "B", "D", "E", "E", "F"));

        Assert.assertArrayEquals(new String[] {"B", "E", "E", "F"}, diff.added().toArray());
        Assert.assertArrayEquals(new String[] {"A", "D"}, diff.removed().toArray());
        Assert.assertArrayEquals(new String[] {"A", "B", "D"}, diff.same().toArray());
    }

    /** */
    @Test
    public void testEmpty() {
        Diff<String> diff = new Diff<>(emptyList(), list1);

        Assert.assertArrayEquals(list1.toArray(), diff.added().toArray());
        Assert.assertArrayEquals(emptyList().toArray(), diff.removed().toArray());
        Assert.assertArrayEquals(emptyList().toArray(), diff.same().toArray());

        diff = new Diff<>(list1, emptyList());

        Assert.assertArrayEquals(list1.toArray(), diff.removed().toArray());
        Assert.assertArrayEquals(emptyArray, diff.added().toArray());
        Assert.assertArrayEquals(emptyArray, diff.same().toArray());

        diff = new Diff<String>(emptyList(), emptyList());

        Assert.assertArrayEquals(emptyArray, diff.removed().toArray());
        Assert.assertArrayEquals(emptyArray, diff.added().toArray());
        Assert.assertArrayEquals(emptyArray, diff.same().toArray());
    }
}
