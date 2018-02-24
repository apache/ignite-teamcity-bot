package org.apache.ignite.ci.analysis;

import java.util.concurrent.TimeUnit;

/**
 * Created by dpavlov on 09.08.2017.
 */
public class Expirable<D> {
    private final long ts;
    private final D data;

    public Expirable(D data) {
        this(System.currentTimeMillis(), data);
    }

    public Expirable(long ts, D data) {
        this.ts = ts;
        this.data = data;
    }

    public long getTs() {
        return ts;
    }

    public D getData() {
        return data;
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - ts;
    }

    public boolean isAgeLessThan(int seconds) {
        return getAgeMs() < TimeUnit.SECONDS.toMillis(seconds);
    }
}
