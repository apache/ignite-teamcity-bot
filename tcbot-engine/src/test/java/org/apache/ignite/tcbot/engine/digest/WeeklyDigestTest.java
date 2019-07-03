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
package org.apache.ignite.tcbot.engine.digest;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import javax.cache.Cache;
import javax.mail.MessagingException;
import org.apache.ignite.Ignite;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.engine.conf.BranchTracked;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.NotificationChannel;
import org.apache.ignite.tcbot.engine.conf.NotificationsConfig;
import org.apache.ignite.tcbot.engine.conf.TcBotJsonConfig;
import org.apache.ignite.tcbot.engine.tracked.IDetailedStatusForTrackedBranch;
import org.apache.ignite.tcbot.engine.ui.DsChainUi;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.notify.IEmailSender;
import org.apache.ignite.tcbot.notify.ISendEmailConfig;
import org.apache.ignite.tcbot.persistence.scheduler.DirectExecNoWaitScheduler;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.creds.ICredentialsProv;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WeeklyDigestTest {

    @Test
    public void generateDigestAndDiff() throws MessagingException {
        IDetailedStatusForTrackedBranch failuresProvider = Mockito.mock(IDetailedStatusForTrackedBranch.class);
        when(failuresProvider.getTrackedBranchTestFailures(anyString(),anyBoolean(),anyInt(),
            any(ICredentialsProv.class), any(SyncMode.class), anyBoolean())).thenAnswer((inv)->{
            DsSummaryUi summaryUi = new DsSummaryUi();

            DsChainUi chainUi = new DsChainUi("", "", "/refs/heads/master");
            summaryUi.addChainOnServer(chainUi);

            return summaryUi;
        });

        ITcBotConfig cfg = Mockito.mock(ITcBotConfig.class);

        TcBotJsonConfig tcBotJsonCfg = new TcBotJsonConfig();
        String master = ITcServerConfig.DEFAULT_TRACKED_BRANCH_NAME;
        String release = "ignite-2.7.5";
        tcBotJsonCfg.addBranch(new BranchTracked().id(master));
        tcBotJsonCfg.addBranch(new BranchTracked().id(release));

        when(cfg.getTrackedBranches()).thenReturn(tcBotJsonCfg);

        when(cfg.notifications()).thenReturn(tcBotJsonCfg.notifications());

        Ignite ignite = Mockito.mock(Ignite.class);
        when(ignite.cache(anyString())).thenAnswer(inv -> {
            Cache cacheMock = Mockito.mock(Cache.class);

            return cacheMock;
        });
        IEmailSender emailSnd = Mockito.mock(IEmailSender.class);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(IDetailedStatusForTrackedBranch.class).toInstance(failuresProvider);

                bind(ITcBotConfig.class).toInstance(cfg);
                bind(IScheduler.class).to(DirectExecNoWaitScheduler.class);
                bind(Ignite.class).toInstance(ignite);
                bind(IEmailSender.class).toInstance(emailSnd);
            }
        });

        DigestService digestSvc = injector.getInstance(DigestService.class);

        ICredentialsProv creds = Mockito.mock(ICredentialsProv.class);

        WeeklyFailuresDigest generate = digestSvc.generateFromCurrentState(master, creds);

        System.out.println(generate.toHtml(null));

        System.out.println(generate.toHtml(generate));

        String email = "tcbot@gmail.com";
        tcBotJsonCfg.notifications().addChannel(new NotificationChannel()
            .email(email)
            .subscribeToDigest(master));


        String rmEmail = "release-manager@apache.org";
        tcBotJsonCfg.notifications().addChannel(new NotificationChannel()
            .email(rmEmail)
            .subscribeToDigest(master).subscribeToDigest(release));

        digestSvc.startBackgroundCheck(creds);

        ArgumentCaptor<String> htmlText = ArgumentCaptor.forClass(String.class);

        verify(emailSnd, times(1))
            .sendEmail(eq(email), anyString(), htmlText.capture(), anyString(), any(ISendEmailConfig.class));

        String htmlEmail = htmlText.getValue();

        System.out.println(htmlEmail);

        assertTrue(htmlEmail.contains("Total executed tests 0"));
        assertTrue(htmlEmail.contains("Digest [master]"));
        assertFalse(htmlEmail.contains("Digest ["+release+"]"));

        verify(emailSnd, times(1))
            .sendEmail(eq(rmEmail), anyString(), htmlText.capture(), anyString(), any(ISendEmailConfig.class));


        String htmlEmailForRm = htmlText.getValue();

        System.out.println(htmlEmailForRm);

        assertTrue(htmlEmailForRm.contains("Total executed tests 0"));
        assertTrue(htmlEmailForRm.contains("Digest [master]"));
        assertTrue(htmlEmailForRm.contains("Digest ["+release+"]"));

    }
}