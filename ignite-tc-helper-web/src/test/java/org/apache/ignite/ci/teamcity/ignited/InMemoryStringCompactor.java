package org.apache.ignite.ci.teamcity.ignited;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryStringCompactor implements IStringCompactor {
    private final Map<Integer, String> idToStr = new ConcurrentHashMap<>();
    private final Map<String, Integer> strToId = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    /** {@inheritDoc} */
    @Override public int getStringId(String val) {
        if (val == null)
            return -1;
        Integer id = strToId.get(val);

        if (id == null) {
            synchronized (this) {
                id = strToId.get(val);
                if (id == null)
                    id = save(val);
            }
        }

        return id;
    }

    private int save(String val) {
        int newId = seq.incrementAndGet();

        strToId.put(val, newId);
        idToStr.put(newId, val);

        return newId;
    }

    /** {@inheritDoc} */
    @Override public String getStringFromId(int id) {
        String val = idToStr.get(id);

        if (val == null) {
            synchronized (this) {
                val = idToStr.get(id);

            }
        }

        return val;
    }

    /** {@inheritDoc} */
    @Override public Integer getStringIdIfPresent(String val) {
        if (val == null)
            return -1;

        Integer id = strToId.get(val);

        if (id == null) {
            synchronized (this) {
                id = strToId.get(val);
            }
        }

        return id;
    }
}
