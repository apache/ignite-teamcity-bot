package org.apache.ignite.ci.teamcity.ignited;
//public  org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor$CompactorEntity
import com.google.common.base.MoreObjects;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.tcbot.persistence.Persisted;

/**
 * Backward compatible holder for entry persisted.
 */
public class IgniteStringCompactor {
    @Persisted
    public static class CompactorEntity {
        @QuerySqlField
        String val;
        @QuerySqlField(index = true)
        int id;

        public CompactorEntity(int candidate, String val) {
            this.id = candidate;
            this.val = val;
        }

        @Override public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("val", val)
                .add("id", id)
                .toString();
        }

        public int id() {
            return id;
        }

        public String val() {
            return val;
        }
    }
}
