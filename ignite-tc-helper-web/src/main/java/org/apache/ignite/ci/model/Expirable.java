package org.apache.ignite.ci.model;

/**
 * Created by dpavlov on 09.08.2017.
 */
public class Expirable<D> {
    private final long ts;
    private final D data;

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
}
