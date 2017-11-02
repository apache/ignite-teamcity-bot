package org.apache.ignite.ci.web.rest.model.chart;

import com.google.common.base.Preconditions;
import java.util.Set;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.web.IBackgroundUpdatable;

/**
 * Created by Дмитрий on 09.08.2017
 */
public class TestsMetrics implements IBackgroundUpdatable {
    public ChartData<SuiteInBranch> failed = new ChartData<>();
    public ChartData<SuiteInBranch> notrun = new ChartData<>();
    public ChartData<SuiteInBranch> muted = new ChartData<>();
    public ChartData<SuiteInBranch> total = new ChartData<>();

    public boolean updateRequired;

    public void initBuilds(Set<SuiteInBranch> builds) {
        builds.forEach(branch -> {
            failed.addEmptyLine(branch, SuiteInBranch::getBranch);
            notrun.addEmptyLine(branch);
            muted.addEmptyLine(branch);
            total.addEmptyLine(branch);
        });
    }

    public int addAxisXLabel(String label) {
        int idx1 = failed.addAxisXLabel(label);
        int idx2 = notrun.addAxisXLabel(label);
        int idx3 = muted.addAxisXLabel(label);
        int idx4 = total.addAxisXLabel(label);
        Preconditions.checkState(idx1 == idx2);
        Preconditions.checkState(idx3 == idx4);
        Preconditions.checkState(idx1 == idx3);
        return idx1;
    }

    @Override public void setUpdateRequired(boolean update) {
        this.updateRequired = update;
    }
}
