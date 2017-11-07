package org.apache.ignite.ci.analysis;

/**
 * Test name combined with run statistics
 */
public class TestRunStat {
    String name;
    RunStat stat;

    public TestRunStat(String name, RunStat stat) {
        this.name = name;
        this.stat = stat;
    }

    public float getFailRate() {
        return stat.getFailRate();
    }

    @Override public String toString() {
        return "TestRunStat{" +
            "name='" + name + '\'' +
            ", failRate=" + getFailPercentPrintable() + "%" +
            '}';
    }

    public String getFailPercentPrintable() {
        return stat.getFailPercentPrintable();
    }

    public String name() {
        return name;
    }

    public long getAverageDurationMs() {
        return stat.getAverageDurationMs();
    }
}
