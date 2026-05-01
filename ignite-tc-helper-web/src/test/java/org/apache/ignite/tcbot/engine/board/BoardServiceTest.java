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

package org.apache.ignite.tcbot.engine.board;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.SingletonScope;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeDao;
import org.apache.ignite.ci.teamcity.ignited.change.RevisionCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.defect.DefectCompacted;
import org.apache.ignite.tcbot.engine.defect.DefectsStorage;
import org.apache.ignite.tcbot.engine.issue.IssuesStorage;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.TcBotPersistenceModule;
import org.apache.ignite.tcbot.persistence.scheduler.DirectExecNoWaitScheduler;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.buildlog.BuildLogCheckResultDao;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcignited.history.BuildStartTimeStorage;
import org.apache.ignite.tcignited.history.SuiteInvocationHistoryDao;
import org.apache.ignite.tcservice.model.vcs.Revision;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import static org.apache.ignite.tcbot.engine.issue.IssueType.newFailure;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BoardServiceTest {
    /** Test ignite port. */
    public static final int TEST_IGNITE_PORT = 64124;

    /** Ignite. */
    private static Ignite ignite;

    private static Injector injector;
    private static FatBuildDao fatBuildDao;
    private static DefectsStorage defectsStorage;
    private static IssuesStorage issuesStorage;
    private static ChangeDao changeDao;
    private static BoardService boardService;

    private final static String tc1 = "apache";
    private final static String tc2 = "private";
    private final static int tcId1 = Math.abs(tc1.hashCode());
    private final static int tcId2 = Math.abs(tc2.hashCode());
    private static int tcSrvCodeCid1;
    private static int tcSrvCodeCid2;
    private final static String branch1 = "branch1";
    private final static String branch2 = "branch2";
    private final static String issueName1 = "issue1";
    private final static String issueName2 = "issue2";
    private final static String issueName3 = "issue3";
    private final static String issueName4 = "issue4";
    private final static String issueName5 = "issue5";
    private final static long nowTime = System.currentTimeMillis();

    private static byte[] commit1 = new byte[] {4, 101, -45};
    private static byte[] commit2 = new byte[] {82, 65, 1};
    private static byte[] commit3 = new byte[] {-120, 0, 37};
    private static byte[] commit4 = new byte[] {0, -7, 37};
    private static byte[] commit5 = new byte[] {-8, -128, 127};

    //issues
    private static Issue issue1 = new Issue(new IssueKey(tc1, 1, issueName1), newFailure, nowTime);
    private static Issue issue2 = new Issue(new IssueKey(tc1, 1, issueName2), newFailure, nowTime);
    private static Issue issue3 = new Issue(new IssueKey(tc1, 2, issueName3), newFailure, nowTime);
    private static Issue issue4 = new Issue(new IssueKey(tc2, 1, issueName4), newFailure, nowTime);
    private static Issue issue5 = new Issue(new IssueKey(tc2, 4, issueName5), newFailure, nowTime);

    //change
    private static ChangeCompacted change1 = createChange(commit1);
    private static ChangeCompacted change2 = createChange(commit2);
    private static ChangeCompacted change3 = createChange(commit3);
    private static ChangeCompacted change4 = createChange(commit4);

    //revision
    private static RevisionCompacted revision1;
    private static RevisionCompacted revision2;
    private static RevisionCompacted revision3;
    private static RevisionCompacted revision4;

    //changes
    private static Map<Integer, ChangeCompacted> emptyBuildChanges = new HashMap<>();
    private static Map<Integer, ChangeCompacted> buildChanges1 = new HashMap<>();
    private static Map<Integer, ChangeCompacted> buildChanges2 = new HashMap<>();

    private static List<RevisionCompacted> buildRevisions1 = new ArrayList<>();
    private static List<RevisionCompacted> buildRevisions2 = new ArrayList<>();
    private static List<RevisionCompacted> buildRevisions3 = new ArrayList<>();

    private static FatBuildCompacted fatBuildCompacted1;
    private static FatBuildCompacted fatBuildCompacted2;
    private static FatBuildCompacted fatBuildCompacted3;
    //fatBuildCompacted4 with the same revisions as the fatBuildCompacted1
    private static FatBuildCompacted fatBuildCompacted4;

    static {
        buildChanges1.put(1, change1);

        buildChanges1.put(2, change2);
    }

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

        TeamcityIgnitedModule module = new TeamcityIgnitedModule();

        injector = Guice.createInjector(module, new IgniteTestModule());

        fatBuildDao = injector.getInstance(FatBuildDao.class);
        defectsStorage = injector.getInstance(DefectsStorage.class);
        issuesStorage = injector.getInstance(IssuesStorage.class);
        changeDao = injector.getInstance(ChangeDao.class);
        boardService = injector.getInstance(BoardService.class);
        IStringCompactor compactor = injector.getInstance(IStringCompactor.class);
        revision1 = createRevision(compactor, commit1);
        revision2 = createRevision(compactor, commit2);
        revision3 = createRevision(compactor, commit3);
        revision4 = createRevision(compactor, commit4);

        buildRevisions1.add(revision1);
        buildRevisions1.add(revision2);

        buildRevisions2.add(revision3);
        buildRevisions2.add(revision4);

        buildRevisions3.add(revision3);
        buildRevisions3.add(revision4);

        fatBuildCompacted1 = createFatBuild(1, buildRevisions1);
        fatBuildCompacted2 = createFatBuild(2, buildRevisions2);
        fatBuildCompacted3 = createFatBuild(3, buildRevisions3);
        fatBuildCompacted4 = createFatBuild(4, buildRevisions1);

        fatBuildDao.init();
        injector.getInstance(SuiteInvocationHistoryDao.class).init();
        injector.getInstance(BuildLogCheckResultDao.class).init();
        injector.getInstance(BuildRefDao.class).init();
        injector.getInstance(BuildStartTimeStorage.class).init();
        injector.getInstance(BuildConditionDao.class).init();

        //save issue names in compactor cache
        compactor.getStringId(issueName1);
        compactor.getStringId(issueName2);
        compactor.getStringId(issueName3);
        compactor.getStringId(issueName4);
        compactor.getStringId(issueName5);

        tcSrvCodeCid1 = compactor.getStringId(tc1);
        tcSrvCodeCid2 = compactor.getStringId(tc2);

        fatBuildDao.putFatBuild(tcId1, fatBuildCompacted1.id(), fatBuildCompacted1);
        fatBuildDao.putFatBuild(tcId1, fatBuildCompacted2.id(), fatBuildCompacted2);
        fatBuildDao.putFatBuild(tcId1, fatBuildCompacted3.id(), fatBuildCompacted3);
        fatBuildDao.putFatBuild(tcId2, fatBuildCompacted1.id(), fatBuildCompacted1);
        fatBuildDao.putFatBuild(tcId2, fatBuildCompacted4.id(), fatBuildCompacted4);

        when(changeDao.getAll(eq(tcId1), any())).thenReturn(buildChanges1);
        when(changeDao.getAll(eq(tcId2), any())).thenReturn(emptyBuildChanges);
    }

    @AfterClass
    public static void stopIgnite() {
        if (ignite != null)
            ignite.close();
    }

    @After
    public void clear() {
        DefectsStorage defectsStorage = injector.getInstance(DefectsStorage.class);
        IssuesStorage issuesStorage = injector.getInstance(IssuesStorage.class);

        defectsStorage.loadAllDefects().stream().forEach(defect0 -> {
            defect0.resolvedByUsernameId(1);
            defectsStorage.save(defect0);
        });

        issuesStorage.removeOldIssues(System.currentTimeMillis(), Integer.MAX_VALUE);
        defectsStorage.removeOldDefects(System.currentTimeMillis(), Integer.MAX_VALUE);
    }

    private static ChangeCompacted createChange(byte[] commit) {
        ChangeCompacted change = mock(ChangeCompacted.class);
        when(change.commitVersion()).thenReturn(commit);
        return change;
    }

    private static RevisionCompacted createRevision(IStringCompactor compactor, byte[] commit) {
        Revision revision = new Revision()
            .version(hex(commit))
            .vcsBranchName(branch1);

        return new RevisionCompacted(compactor, revision);
    }

    private static FatBuildCompacted createFatBuild(int id, List<RevisionCompacted> revisions) {
        FatBuildCompacted build = new FatBuildCompacted();

        build.setId(id);
        setField(build, "revisions", revisions.toArray(new RevisionCompacted[0]));

        return build;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);

            field.setAccessible(true);
            field.set(target, value);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder res = new StringBuilder(bytes.length * 2);

        for (byte b : bytes)
            res.append(String.format("%02x", b & 0xff));

        return res.toString();
    }

    /**
     * Test that convert the same issue to defect does not create new defect
     */
    @Test
    public void testMergingTheSameIssue() {
        issuesStorage.saveIssue(issue1);

        boardService.issuesToDefects();

        DefectCompacted defect = singleDefect();

        assertEquals(1, defect.buildsInvolved().size());
        assertEquals((int)issue1.issueKey().getBuildId(), defect.buildsInvolved().get((int)issue1.issueKey().getBuildId()).build().id());
        assertEquals(1, defectsStorage.loadAllDefects().size());

        boardService.issuesToDefects();

        defect = singleDefect();

        assertEquals(1, defect.buildsInvolved().size());
        assertEquals((int)issue1.issueKey().buildId, defect.buildsInvolved().get((int)issue1.issueKey().buildId).build().id());
        assertEquals(1, defectsStorage.loadAllDefects().size());

    }

    /**
     * Test that two issues from the same build converted to one defect
     */
    @Test
    public void testMergingIssuesFromTheSameBuild() {
        issuesStorage.saveIssue(issue1);
        issuesStorage.saveIssue(issue2);

        boardService.issuesToDefects();

        DefectCompacted defect = singleDefect();

        assertEquals(1, defect.buildsInvolved().size());
        assertEquals((int)issue2.issueKey().buildId, defect.buildsInvolved().get((int)issue2.issueKey().buildId).build().id());
        assertEquals(1, defectsStorage.loadAllDefects().size());

    }

    /**
     * Test that two issues from different build and different revisions but with the same changes converted to one defect
     */
    @Test
    public void testMergingIssuesWithTheSameChanges() {
        issuesStorage.saveIssue(issue2);
        issuesStorage.saveIssue(issue3);

        boardService.issuesToDefects();

        DefectCompacted defect = singleDefect();

        assertEquals(2, defect.buildsInvolved().size());
        assertEquals((int)issue2.issueKey().buildId, defect.buildsInvolved().get((int)issue2.issueKey().buildId).build().id());
        assertEquals((int)issue3.issueKey().buildId, defect.buildsInvolved().get((int)issue3.issueKey().buildId).build().id());
        assertEquals(1, defectsStorage.loadAllDefects().size());

    }

    /**
     * Test that two issues from different build and no changes but with the same revisions converted to one defect
     */
    @Test
    public void testMergingIssuesWithTheSameRevisions() {
        issuesStorage.saveIssue(issue4);

        boardService.issuesToDefects();

        issuesStorage.removeOldIssues(System.currentTimeMillis(), Integer.MAX_VALUE);

        issuesStorage.saveIssue(issue5);

        boardService.issuesToDefects();

        DefectCompacted defect = singleDefect();

        assertEquals(2, defect.buildsInvolved().size());
        assertEquals((int)issue4.issueKey().buildId, defect.buildsInvolved().get((int)issue4.issueKey().buildId).build().id());
        assertEquals((int)issue5.issueKey().buildId, defect.buildsInvolved().get((int)issue5.issueKey().buildId).build().id());
        assertEquals(1, defectsStorage.loadAllDefects().size());
    }

    /**
     * Test that two issues converted to two defects
     */
    @Test
    public void testConvertIssuesToDifferentDefects() {
        issuesStorage.saveIssue(issue1);

        boardService.issuesToDefects();

        issuesStorage.removeOldIssues(System.currentTimeMillis(), Integer.MAX_VALUE);

        issuesStorage.saveIssue(issue4);

        boardService.issuesToDefects();

        Collection<DefectCompacted> defects = defectsStorage.loadAllDefects();

        assertEquals(2, defects.size());

        DefectCompacted defect1 = defectForBuild(defects, issue1.issueKey().buildId);
        DefectCompacted defect2 = defectForBuild(defects, issue4.issueKey().buildId);

        assertEquals(1, defect1.buildsInvolved().size());
        assertEquals((int)issue1.issueKey().buildId, defect1.buildsInvolved().get((int)issue1.issueKey().buildId).build().id());

        assertEquals(1, defect2.buildsInvolved().size());
        assertEquals((int)issue4.issueKey().buildId, defect2.buildsInvolved().get((int)issue4.issueKey().buildId).build().id());
    }

    private DefectCompacted singleDefect() {
        Collection<DefectCompacted> defects = defectsStorage.loadAllDefects();

        assertEquals(1, defects.size());

        return defects.iterator().next();
    }

    private DefectCompacted defectForBuild(Collection<DefectCompacted> defects, long buildId) {
        for (DefectCompacted defect : defects) {
            if (defect.buildsInvolved().containsKey((int)buildId))
                return defect;
        }

        throw new AssertionError("Defect for build " + buildId + " was not found");
    }

    private static class IgniteTestModule extends AbstractModule {
        /** {@inheritDoc} */
        @Override protected void configure() {
            bind(Ignite.class).toInstance(ignite);
            bind(IScheduler.class).to(DirectExecNoWaitScheduler.class).in(new SingletonScope());

            ITcBotConfig cfg = Mockito.mock(ITcBotConfig.class);
            bind(ITcBotConfig.class).toInstance(cfg);
            bind(IDataSourcesConfigSupplier.class).toInstance(cfg);

            install(new TcBotPersistenceModule());
        }
    }
}
