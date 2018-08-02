package org.apache.ignite.ci.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class Diff<T extends Comparable<T>> {

    private final List<T> added = new ArrayList<>();

    private final List<T> rmvd = new ArrayList<>();

    private final List<T> same = new ArrayList<>();



    /**
     * @param c1 First collection to compare.
     * @param c2 Seconds collection to compare.
     */
    public Diff(Collection<T> c1, Collection<T> c2) {
        if (c1.isEmpty()) {
            added.addAll(c2);

            return;
        }

        if (c2.isEmpty()) {
            rmvd.addAll(c1);

            return;
        }

        Iterator<T> oldIter = c1.iterator();
        Iterator<T> newIter = c2.iterator();

        int cmp = 0;

        T e1 = null;
        T e2 = null;

        while (oldIter.hasNext() || newIter.hasNext()) {
            if (oldIter.hasNext() && cmp <= 0)
                e1 = oldIter.next();

            if (newIter.hasNext() && cmp >= 0)
                e2 = newIter.next();

            assert e1 != null;
            assert e2 != null;

            cmp = e1.compareTo(e2);

            if (cmp < 0)
                rmvd.add(e1);
            else if (cmp > 0)
                added.add(e2);
            else
                same.add(e1);

            if (!oldIter.hasNext()) {
                if (cmp < 0)
                    added.add(e2);

                while (newIter.hasNext())
                    added.add(newIter.next());
            }

            if (!newIter.hasNext()) {
                if (cmp > 0)
                    rmvd.add(e1);

                while (oldIter.hasNext())
                    rmvd.add(oldIter.next());
            }
        }
    }

    /** */
    public List<T> added() {
        return added;
    }

    /** */
    public List<T> removed() {
        return rmvd;
    }

    /** */
    public List<T> same() {
        return same;
    }

}
