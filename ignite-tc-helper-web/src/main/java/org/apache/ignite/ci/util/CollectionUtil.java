package org.apache.ignite.ci.util;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Дмитрий on 07.11.2017.
 */
public class CollectionUtil {
    public static <T> List<T> top(Stream<T> data, int count, Comparator<T> comparator) {
        Comparator<T> reversedComparator = (o1, o2) -> -comparator.compare(o1, o2);
        return data.sorted(reversedComparator).limit(count).collect(Collectors.toList());
    }
}
