package org.apache.ignite.ci.analysis;

/**
 * Versioned entry, if entry of previous version cached it may be reloaded from source
 */
public interface IVersionedEntity {
    int version();
    int latestVersion();

    default <V extends IVersionedEntity> boolean isOutdatedEntityVersion() {
        return version() < latestVersion();
    }
}
