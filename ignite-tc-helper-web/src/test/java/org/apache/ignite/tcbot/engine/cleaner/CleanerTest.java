/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.tcbot.engine.cleaner;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.SingletonScope;
import java.lang.reflect.Field;
import java.util.Random;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.jiraservice.IJiraIntegrationProvider;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;
import org.apache.ignite.tcbot.engine.chain.TestCompactedMult;
import org.apache.ignite.tcbot.engine.conf.CleanerConfig;
import org.apache.ignite.tcbot.engine.conf.ICleanerConfig;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.defect.DefectCompacted;
import org.apache.ignite.tcbot.engine.defect.DefectsStorage;
import org.apache.ignite.tcbot.engine.issue.IssuesStorage;
import org.apache.ignite.tcbot.persistence.TcBotPersistenceModule;
import org.apache.ignite.tcbot.persistence.scheduler.DirectExecNoWaitScheduler;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.build.TestCompactedV2;
import org.apache.ignite.tcignited.buildlog.BuildLogCheckResultDao;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcignited.history.BuildStartTimeStorage;
import org.apache.ignite.tcignited.history.SuiteInvocationHistoryDao;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import static java.time.ZonedDateTime.now;
import static org.apache.ignite.tcbot.engine.issue.IssueType.newFailure;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CleanerTest {

    /** Server Name for test. */
    public static final String APACHE = "apachetest";

    /** Test ignite port. */
    public static final int TEST_IGNITE_PORT = 64124;

    /** Ignite. */
    private static Ignite ignite;

    /**
     *
     */
    @BeforeClass
    public static void startIgnite() {
        IgniteConfiguration cfg = new IgniteConfiguration();
        final TcpDiscoverySpi spi = new TcpDiscoverySpi();
        int locPort = TEST_IGNITE_PORT;

        spi.setLocalPort(locPort);
        spi.setLocalPortRange(1);
        spi.setIpFinder(new TcHelperDb.LocalOnlyTcpDiscoveryIpFinder(locPort));

        cfg.setDiscoverySpi(spi);

        ignite = Ignition.start(cfg);
    }

    /**
     *
     */
    @AfterClass
    public static void stopIgnite() {
        if (ignite != null)
            ignite.close();
    }

    @Test
    public void testCleaner() throws Exception {
        TeamcityIgnitedModule module = new TeamcityIgnitedModule();

        Injector injector = Guice.createInjector(module, new IgniteAndSchedulerTestModule());

        FatBuildDao fatBuildDao = injector.getInstance(FatBuildDao.class);
        DefectsStorage defectsStorage = injector.getInstance(DefectsStorage.class);
        IssuesStorage issuesStorage = injector.getInstance(IssuesStorage.class);
        Cleaner cleaner = injector.getInstance(Cleaner.class);

        fatBuildDao.init();
        injector.getInstance(SuiteInvocationHistoryDao.class).init();
        injector.getInstance(BuildLogCheckResultDao.class).init();
        injector.getInstance(BuildRefDao.class).init();
        injector.getInstance(BuildStartTimeStorage.class).init();
        injector.getInstance(BuildConditionDao.class).init();

        FatBuildEntry.fatBuildDao = fatBuildDao;
        DefectEntry.defectsStorage = defectsStorage;
        IssueEntry.issuesStorage = issuesStorage;

        Random rnd = new Random();

        final String tc1 = "apache";
        final String tc2 = "private";
        final int tcId1 = Math.abs(tc1.hashCode());
        final int tcId2 = Math.abs(tc2.hashCode());
        final int safeDays = 5;
        final long nowTime = now().toInstant().toEpochMilli();

        FatBuildEntry oldBuildToRemove1 = FatBuildEntry.createFatBuildEntry(tcId1, 1, now().minusDays(safeDays + 5).toInstant().toEpochMilli(), true);
        FatBuildEntry oldBuildToSave1 = FatBuildEntry.createFatBuildEntry(tcId1, 2, now().minusDays(safeDays + 5).toInstant().toEpochMilli(), true);
        FatBuildEntry buildToSave1 = FatBuildEntry.createFatBuildEntry(tcId1, 3, now().minusDays(safeDays - 2).toInstant().toEpochMilli(), true);
        FatBuildEntry notSavedBuild = FatBuildEntry.createFatBuildEntry(tcId1, 4, now().minusDays(safeDays - 2).toInstant().toEpochMilli(), false);

        DefectEntry oldOpenedDefectToSave1 = DefectEntry.createDefectEntry(11, tcId1, oldBuildToSave1.build, -1);
        DefectEntry oldOpenedDefectToSave2 = DefectEntry.createDefectEntry(15, tcId2, oldBuildToRemove1.build, now().minusDays(safeDays + 5).toInstant().toEpochMilli());
        DefectEntry oldClosedWithBrokenConsistencyDefectToSave3 = DefectEntry.createDefectEntry(12, tcId1, notSavedBuild.build, now().minusDays(safeDays + 5).toInstant().toEpochMilli());
        DefectEntry oldClosedDefectToRemove1 = DefectEntry.createDefectEntry(13, tcId1, oldBuildToRemove1.build, now().minusDays(safeDays + 5).toInstant().toEpochMilli());
        DefectEntry oldClosedWithBrokenConsistencyDefectToRemove2 = DefectEntry.createDefectEntry(14, tcId1, notSavedBuild.build, now().minusDays(safeDays + 1000).toInstant().toEpochMilli());

        IssueEntry issueToRemove1 = IssueEntry.createIssueEntry(tc1, oldBuildToRemove1.buildId, nowTime);
        IssueEntry issueWithBrokenConsistencyToRemove2 = IssueEntry.createIssueEntry(tc1, Math.abs(rnd.nextInt()), now().minusDays(safeDays + 1000).toInstant().toEpochMilli());
        IssueEntry issueToSave1 = IssueEntry.createIssueEntry(tc2, oldBuildToRemove1.buildId, nowTime);
        IssueEntry issueWithBrokenConsistencyToSave2 = IssueEntry.createIssueEntry(tc1, Math.abs(rnd.nextInt()), nowTime);

        cleaner.clean();

        Assert.assertNull(fatBuildDao.getFatBuild(oldBuildToRemove1.tcId, oldBuildToRemove1.buildId));
        Assert.assertNotNull(fatBuildDao.getFatBuild(oldBuildToSave1.tcId, oldBuildToSave1.buildId));
        Assert.assertNotNull(fatBuildDao.getFatBuild(buildToSave1.tcId, buildToSave1.buildId));

        Assert.assertNull(defectsStorage.load(oldClosedDefectToRemove1.id));
        Assert.assertNull(defectsStorage.load(oldClosedWithBrokenConsistencyDefectToRemove2.id));
        Assert.assertNotNull(defectsStorage.load(oldOpenedDefectToSave1.id));
        Assert.assertNotNull(defectsStorage.load(oldOpenedDefectToSave2.id));
        Assert.assertNotNull(defectsStorage.load(oldClosedWithBrokenConsistencyDefectToSave3.id));

        Assert.assertNull(issuesStorage.getIssue(issueToRemove1.issueKey));
        Assert.assertNull(issuesStorage.getIssue(issueWithBrokenConsistencyToRemove2.issueKey));
        Assert.assertNotNull(issuesStorage.getIssue(issueToSave1.issueKey));
        Assert.assertNotNull(issuesStorage.getIssue(issueWithBrokenConsistencyToSave2.issueKey));
    }

    private static class FatBuildEntry {
        int tcId;
        int buildId;
        FatBuildCompacted build;

        static FatBuildDao fatBuildDao;
        static Field setterBuildStartDate;

        static {
            try {
                setterBuildStartDate = FatBuildCompacted.class.getDeclaredField("startDate");
            }
            catch (NoSuchFieldException e) {
                e.printStackTrace();
            }

            setterBuildStartDate.setAccessible(true);
        }

        private FatBuildEntry(int tcId, int buildId, FatBuildCompacted build) {
            this.tcId = tcId;
            this.buildId = buildId;
            this.build = build;
        }

        static FatBuildEntry createFatBuildEntry(int srvId, int buildId, long startDate, boolean save) throws Exception{
            FatBuildCompacted fatBuildCompacted = new FatBuildCompacted();
            fatBuildCompacted.withId(buildId);

            setterBuildStartDate.setLong(fatBuildCompacted, startDate);

            FatBuildEntry fatBuildEntry = new FatBuildEntry(srvId, buildId, fatBuildCompacted);

            if (save)
                fatBuildDao.putFatBuild(fatBuildEntry.tcId, fatBuildEntry.buildId, fatBuildEntry.build);

            return fatBuildEntry;
        }
    }

    private static class DefectEntry {
        int id;
        int tcId;
        DefectCompacted defect;
        FatBuildCompacted build;

        static DefectsStorage defectsStorage;
        static Field setterResolvedTs;

        static {
            try {
                setterResolvedTs = DefectCompacted.class.getDeclaredField("resolvedTs");
            }
            catch (NoSuchFieldException e) {
                e.printStackTrace();
            }

            setterResolvedTs.setAccessible(true);
        }

        public DefectEntry(int id, int tcId, DefectCompacted defect,
            FatBuildCompacted build) {
            this.id = id;
            this.tcId = tcId;
            this.defect = defect;
            this.build = build;
        }

        static DefectEntry createDefectEntry(int id, int tcId, FatBuildCompacted build, long resolvedTs) throws IllegalAccessException {
            DefectCompacted defect = new DefectCompacted(id);
            defect.tcSrvId(tcId);
            defect.computeIfAbsent(build);
            setterResolvedTs.setLong(defect, resolvedTs);

            defectsStorage.save(defect);

            return new DefectEntry(id, tcId, defect, build);
        }
    }

    private static class IssueEntry {
        IssueKey issueKey;
        Issue issue;

        static IssuesStorage issuesStorage;

        public IssueEntry(IssueKey issueKey, Issue issue) {
            this.issueKey = issueKey;
            this.issue = issue;
        }

        static IssueEntry createIssueEntry(String tc, Integer buildId, Long detectedTs) {
            IssueKey issueKey = new IssueKey(tc, buildId, "issue" + new Random().nextLong());
            Issue issue = new Issue(issueKey, newFailure, 888888L);
            issue.detectedTs = detectedTs;

            issuesStorage.saveIssue(issue);

            return new IssueEntry(issueKey, issue);
        }
    }

    private static class IgniteAndSchedulerTestModule extends AbstractModule {
        /** {@inheritDoc} */
        @Override protected void configure() {
            bind(Ignite.class).toInstance(ignite);
            bind(IScheduler.class).to(DirectExecNoWaitScheduler.class).in(new SingletonScope());

            final IJiraIntegrationProvider jiraProv = Mockito.mock(IJiraIntegrationProvider.class);
            bind(IJiraIntegrationProvider.class).toInstance(jiraProv);

            ITcBotConfig cfg = Mockito.mock(ITcBotConfig.class);

            ICleanerConfig cCfg = mock(CleanerConfig.class);

            when(cCfg.enabled()).thenReturn(true);
            when(cCfg.numOfItemsToDel()).thenReturn(100);
            when(cCfg.safeDaysForCaches()).thenReturn(5);

            when(cfg.getCleanerConfig()).thenReturn(cCfg);

            bind(ITcBotConfig.class).toInstance(cfg);
            bind(IDataSourcesConfigSupplier.class).toInstance(cfg);

            install(new TcBotPersistenceModule());
        }
    }
}
