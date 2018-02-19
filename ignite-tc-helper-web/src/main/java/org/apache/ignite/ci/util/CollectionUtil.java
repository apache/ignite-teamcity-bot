package org.apache.ignite.ci.util;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Дмитрий on 07.11.2017.
 */
public class CollectionUtil {
    public static <T> List<T> top(Stream<T> data, int count, Comparator<T> comp) {
        Comparator<T> reversedComp = comp.reversed();
        return data.sorted(reversedComp).limit(count).collect(Collectors.toList());
    }
}
