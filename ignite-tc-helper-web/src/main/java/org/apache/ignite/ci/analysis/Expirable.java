package org.apache.ignite.ci.analysis;

import java.util.concurrent.TimeUnit;
import org.apache.ignite.ci.db.Persisted;

/**
 * Wrapper for timestamped entry to be reloaded later.
 */
@Persisted
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

    public boolean isAgeLessThanSecs(int seconds) {
        return getAgeMs() < TimeUnit.SECONDS.toMillis(seconds);
    }
}
