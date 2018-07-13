package org.apache.ignite.ci.issue;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import org.apache.ignite.ci.analysis.RunStat;

public class EventTemplates {
    public static final EventTemplate newFailure = new EventTemplate(
            new int[]{RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK},
            new int[]{RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE}
    );

    public static final EventTemplate fixOfFailure = new EventTemplate(
            new int[]{RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE},
            new int[]{RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK}
    );

    public static final EventTemplate newCriticalFailure = new EventTemplate(
            new int[]{RunStat.RES_OK_OR_FAILURE, RunStat.RES_OK_OR_FAILURE, RunStat.RES_OK_OR_FAILURE, RunStat.RES_OK_OR_FAILURE, RunStat.RES_OK_OR_FAILURE},
            new int[]{RunStat.RES_CRITICAL_FAILURE, RunStat.RES_CRITICAL_FAILURE, RunStat.RES_CRITICAL_FAILURE}
    );

    public static ArrayList<EventTemplate> templates;

    static {
        templates = Lists.newArrayList(newFailure, newCriticalFailure, fixOfFailure);
    }
}
