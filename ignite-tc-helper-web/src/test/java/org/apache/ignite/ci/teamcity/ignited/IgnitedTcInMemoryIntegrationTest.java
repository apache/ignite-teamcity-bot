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
package org.apache.ignite.ci.teamcity.ignited;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.JAXBException;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.di.scheduler.DirectExecNoWaitSheduler;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.teamcity.pure.ITeamcityHttpConnection;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.STRINGS_CACHE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test for ignite persistence
 */
public class IgnitedTcInMemoryIntegrationTest {
    /** Server Name for test. */
    public static final String APACHE = "apache";
    /** Ignite. */
    private static Ignite ignite;

    /**
     *
     */
    @BeforeClass
    public static void startIgnite() {
        ignite = Ignition.start();
    }

    /**
     *
     */
    @AfterClass
    public static void stopIgnite() {
        ignite.close();
    }

    @Test
    public void saveAndLoadBuildReference() throws IOException {
        ITeamcityHttpConnection http = Mockito.mock(ITeamcityHttpConnection.class);

        when(http.sendGet(anyString(), anyString())).thenAnswer(
            (invocationOnMock) -> {
                String url = invocationOnMock.getArgument(1);

                if (url.contains("/app/rest/latest/builds?locator=defaultFilter:false,count:1000,start:1000"))
                    return getClass().getResourceAsStream("/buildHistoryMasterPage2.xml");

                if (url.contains("/app/rest/latest/builds?locator=defaultFilter:false"))
                    return getClass().getResourceAsStream("/buildHistoryMaster.xml");

                throw new FileNotFoundException(url);
            }
        );

        TeamcityIgnitedModule module = new TeamcityIgnitedModule();

        module.overrideHttp(http);

        Injector injector = Guice.createInjector(module, new AbstractModule() {
            @Override protected void configure() {
                bind(Ignite.class).toInstance(ignite);
                bind(IScheduler.class).to(DirectExecNoWaitSheduler.class);
            }
        });

        ITeamcityIgnited srv = injector.getInstance(ITeamcityIgnitedProvider.class).server(APACHE, creds());

        String buildTypeId = "IgniteTests24Java8_RunAll";
        String branchName = "<default>";
        List<BuildRef> hist = srv.getBuildHistory(buildTypeId, branchName);
        //todo mult branches including pull/4926/head

        assertTrue(!hist.isEmpty());

        for (BuildRef h : hist) {
            System.out.println(h);

            assertEquals(buildTypeId, h.suiteId());

            assertEquals("refs/heads/master", h.branchName());
        }

        ignite.cache(STRINGS_CACHE).forEach(
            (e) -> {
                System.out.println(e.getValue());
            }
        );
    }

    /**
     *
     */
    @NotNull public ICredentialsProv creds() {
        ICredentialsProv mock = Mockito.mock(ICredentialsProv.class);

        when(mock.hasAccess(anyString())).thenReturn(true);
        when(mock.getUser(anyString())).thenReturn("mtcga");
        when(mock.getPassword(anyString())).thenReturn("123");

        return mock;
    }

    @Test
    public void testFatBuild() throws JAXBException, IOException {
        InputStream stream = getClass().getResourceAsStream("/build.xml");
        Build refBuild = XmlUtil.load(Build.class, new InputStreamReader(stream));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Ignite.class).toInstance(ignite);
                bind(IStringCompactor.class).to(IgniteStringCompactor.class);
            }
        });

        FatBuildDao instance = injector.getInstance(FatBuildDao.class);
        instance.init();

        int srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(APACHE);
        int i = instance.saveChunk(srvIdMaskHigh, Collections.singletonList(refBuild));
        assertEquals(1, i);

        FatBuildCompacted fatBuild = instance.getFatBuild(srvIdMaskHigh, 2039380);

        Build actBuild = fatBuild.toBuild(injector.getInstance(IStringCompactor.class));

        String save = XmlUtil.save(actBuild);

        System.out.println(save);

        FileWriter writer = new FileWriter("src/test/resources/build2.xml");
        writer.write(save);
        writer.close();

        assertEquals(refBuild.getId(), actBuild.getId());
        assertEquals(refBuild.status(), actBuild.status());
        assertEquals(refBuild.state(), actBuild.state());
        assertEquals(refBuild.buildTypeId(), actBuild.buildTypeId());
        assertEquals(refBuild.getStartDate(), actBuild.getStartDate());
        assertEquals(refBuild.getFinishDate(), actBuild.getFinishDate());
        BuildType refBt = refBuild.getBuildType();
        BuildType actBt = actBuild.getBuildType();
        assertEquals(refBt.getName(), actBt.getName());
        assertEquals(refBt.getProjectId(), actBt.getProjectId());
        assertEquals(refBt.getId(), actBt.getId());
    }
}
