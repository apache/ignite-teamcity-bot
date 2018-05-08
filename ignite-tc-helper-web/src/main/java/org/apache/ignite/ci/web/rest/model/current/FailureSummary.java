package org.apache.ignite.ci.web.rest.model.current;

import com.google.common.base.Objects;
import javax.annotation.Nullable;

public class FailureSummary {
    /** Registered number of failures from TC helper DB */
    @Nullable public Integer failures;

    /** Registered number of runs from TC helper DB */
    @Nullable public Integer runs;

    /** Registered percent of fails from TC helper DB, comma is always used as separator char. */
    @Nullable public String failureRate;

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FailureSummary summary = (FailureSummary)o;
        return Objects.equal(failures, summary.failures) &&
            Objects.equal(runs, summary.runs) &&
            Objects.equal(failureRate, summary.failureRate);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(failures, runs, failureRate);
    }
}
