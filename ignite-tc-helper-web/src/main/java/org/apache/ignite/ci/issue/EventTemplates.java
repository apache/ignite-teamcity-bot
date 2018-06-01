package org.apache.ignite.ci.issue;

import org.apache.ignite.ci.analysis.RunStat;

public class EventTemplates {
    public static final EventTemplate newFailure = new EventTemplate(
            new int[]{RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK},
            new int[]{RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE}
    );

    public static final EventTemplate fixOfFailure = new EventTemplate(
            new int[]{RunStat.RES_FAILURE},
            new int[]{RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK}
    );

    public static final EventTemplate newCriticalFailureA = new EventTemplate(
            new int[]{RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK},
            new int[]{RunStat.RES_CRITICAL_FAILURE, RunStat.RES_CRITICAL_FAILURE, RunStat.RES_CRITICAL_FAILURE}
    );

    public static final EventTemplate newCriticalFailureB = new EventTemplate(
            new int[]{RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE},
            new int[]{RunStat.RES_CRITICAL_FAILURE, RunStat.RES_CRITICAL_FAILURE, RunStat.RES_CRITICAL_FAILURE}
    );
}
