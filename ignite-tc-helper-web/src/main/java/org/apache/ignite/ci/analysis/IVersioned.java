package org.apache.ignite.ci.analysis;

/**
 * Versioned entry, if entry of previous version cached it may be reloaded from source
 */
public interface IVersioned {
    int version();
    int latestVersion();
}
