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

package org.apache.ignite.ci.tcbot.visa;

import com.google.common.base.Strings;
import org.apache.ignite.ci.di.cache.GuavaCached;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnited;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.jira.ignited.IJiraIgnited;
import org.apache.ignite.ci.jira.ignited.IJiraIgnitedProvider;
import org.apache.ignite.ci.jira.ignited.TicketCompacted;
import org.apache.ignite.ci.jira.pure.Ticket;
import org.apache.ignite.ci.tcbot.conf.IGitHubConfig;
import org.apache.ignite.ci.tcbot.conf.IJiraServerConfig;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import javax.ws.rs.QueryParam;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public class BranchTicketMatcher {
    /** Config. */
    @Inject private ITcBotConfig cfg;

    /** GitHub connection ignited provider. */
    @Inject private IGitHubConnIgnitedProvider gitHubProvider;

    /** JIRA provider */
    @Inject private IJiraIgnitedProvider jiraIgnProv;

    @Nullable
    public String resolveTcBranch(IGitHubConnIgnited gitHubConnIgnited,
                                  Ticket ticket, IJiraServerConfig jiraCfg) {
        String branchNumPrefix = jiraCfg.branchNumPrefix();

        if (Strings.isNullOrEmpty(branchNumPrefix)) {
            //an easy way, no special branch and ticket mappings specified, use project code.
            int ticketId = ticket.keyWithoutProject(jiraCfg.projectCodeForVisa());

            return gitHubConnIgnited.gitBranchPrefix() + ticketId;
        }

        String branchJiraIdentification = findFixPrefixedNumber(ticket.fields.summary, branchNumPrefix);

        if (!Strings.isNullOrEmpty(branchJiraIdentification))
            return convertJiraToGit(branchJiraIdentification, gitHubConnIgnited, branchNumPrefix);

        String branchJiraIdentification2 = findFixPrefixedNumber(ticket.fields.customfield_11050, branchNumPrefix);

        return convertJiraToGit(branchJiraIdentification2, gitHubConnIgnited, branchNumPrefix);

    }

    /**
     * Converts JIRA notation branch name to actual git branch name. Usually it is just lower-casing, but any mapping may be configured.
     * @param branchJiraIdentification Branch jira identification.
     * @param gitHubConnIgnited Git hub connection ignited.
     * @param branchNumPrefix Branch number prefix.
     */
    public String convertJiraToGit(String branchJiraIdentification, IGitHubConnIgnited gitHubConnIgnited,
                                   String branchNumPrefix) {
        if (Strings.isNullOrEmpty(branchJiraIdentification))
            return null;

        return gitHubConnIgnited.gitBranchPrefix() + branchJiraIdentification.substring(branchNumPrefix.length());
    }

    /**
     * @param tickets Tickets.
     * @param pr Pr.
     * @param jiraCfg Jira config.
     */
    @Nullable public String determineJiraId(Collection<Ticket> tickets, PullRequest pr, IJiraServerConfig jiraCfg) {
        String branchNumPrefix = jiraCfg.branchNumPrefix();

        if (Strings.isNullOrEmpty(branchNumPrefix)) {
            //an easy way, no special branch and ticket mappings specified, use project code.
            String jiraPrefix = jiraCfg.projectCodeForVisa() + TicketCompacted.PROJECT_DELIM;

            return findFixPrefixedNumber(pr.getTitle(), jiraPrefix);
        }

        String prTitle = pr.getTitle();

        String branchNum = findFixPrefixedNumber(prTitle, branchNumPrefix);

        if (branchNum == null) // PR does not mention
            return null;

        return findTicketMentions(tickets, branchNum);
    }

    @SuppressWarnings("WeakerAccess")
    @GuavaCached(maximumSize = 3000, expireAfterWriteSecs = 60, cacheNullRval = true)
    protected String findTicketMentions(String srvCode, @Nullable String branchNum) {
        return findTicketMentions(jiraIgnProv.server(srvCode).getTickets(), branchNum);
    }

    @Nullable private String findTicketMentions(Collection<Ticket> tickets, @Nullable String branchNum) {
        if (Strings.isNullOrEmpty(branchNum))
            return null;

        return tickets.stream()
                .map(t -> t.key)
                .filter(k -> Objects.equals(k, branchNum))
                .findFirst()
                .orElseGet(() -> findTicketMentionsInSupplementaryFields(tickets, branchNum));
    }

    @Nullable private String findTicketMentionsInSupplementaryFields(Collection<Ticket> tickets, String branchNum) {
        if (Strings.isNullOrEmpty(branchNum))
            return null;

        return tickets.stream()
                .filter(t -> mentionsBranch(branchNum, t))
                .findFirst()
                .map(t -> t.key)
                .orElse(null);
    }

    /**
     * @param branchName Full branch name in jira.
     * @param ticket Ticket.
     */
    public boolean mentionsBranch(String branchName, Ticket ticket) {
        String summary = ticket.fields.summary;
        if (summary != null && summary.contains(branchName))
            return true;

        String val = ticket.fields.customfield_11050;
        if (val != null && val.contains(branchName))
            return true;

        return false;
    }


    /**
     * @param prTitle Pull Request title prefix or other text to find constant-prefix text.
     * @param prefix Ticket prefix.
     * @return Branch number or null.
     */
    @Nullable public static String findFixPrefixedNumber(@Nullable String prTitle, @NotNull String prefix) {
        if(Strings.isNullOrEmpty(prTitle))
            return null;

        int idxOfBranchNum = prTitle.toUpperCase().indexOf(prefix.toUpperCase());

        if (idxOfBranchNum < 0)
            return null;

        int beginIdx = prefix.length() + idxOfBranchNum;
        int endIdx = beginIdx;

        while (endIdx < prTitle.length() && Character.isDigit(prTitle.charAt(endIdx)))
            endIdx++;

        if (endIdx == beginIdx)
            return null;

        return prefix + prTitle.substring(beginIdx, endIdx);
    }

    public static class TicketNotFoundException extends Exception {
        public TicketNotFoundException(String msg) {
            super(msg);
        }

        public TicketNotFoundException(String msg, Exception e) {
            super(msg, e);
        }
    }


    public String resolveTicketFromBranch(String srvCode, String ticketFullName, String branchForTc) throws TicketNotFoundException {
        if(!Strings.isNullOrEmpty(ticketFullName))
            return ticketFullName; //old code probably not needed now; ticketFullName = ticketFullName.toUpperCase().startsWith(prefix) ? ticketFullName : prefix + ticketFullName;

        IJiraServerConfig jiraCfg = cfg.getJiraConfig(srvCode);
        IGitHubConfig gitConfig = cfg.getGitConfig(srvCode);

        PullRequest pr; // filled only when special PR found

        String ticketPrefix;
        try {
            String branchNumPrefix = jiraCfg.branchNumPrefix();

            ticketPrefix = Strings.isNullOrEmpty(branchNumPrefix) ?
                    jiraCfg.projectCodeForVisa() + TicketCompacted.PROJECT_DELIM
                    : branchNumPrefix;

            String prLessTicket = prLessTicket(branchForTc, ticketPrefix, gitConfig);
            if (!Strings.isNullOrEmpty(prLessTicket)) {
                if (Strings.isNullOrEmpty(branchNumPrefix)) {
                    //Default, simple case

                    return prLessTicket; //find out PRless ticket,
                } else {
                    // PR less ticket only mentioned in real ticket
                    String ticket = findTicketMentions(srvCode, prLessTicket);

                    if (!Strings.isNullOrEmpty(ticket))
                        return ticket; // found real JIRA ticket for comment
                }
            }

            pr = findPrForBranch(srvCode, branchForTc);
            if (pr != null) {
                String ticketFromPr;
                if (Strings.isNullOrEmpty(branchNumPrefix)) {
                    //Default, simple case, branch name matching gives us a ticket
                    ticketFromPr = findFixPrefixedNumber(pr.getTitle(), ticketPrefix);
                } else {
                    String jiraBranchNum = findFixPrefixedNumber(pr.getTitle(), branchNumPrefix);

                    ticketFromPr = Strings.isNullOrEmpty(jiraBranchNum)
                            ? null
                            : findTicketMentions(srvCode, jiraBranchNum);
                }

                if (!Strings.isNullOrEmpty(ticketFromPr))
                    return ticketFromPr; // found real JIRA ticket for comment
            }
        } catch (Exception e) {
            throw new TicketNotFoundException("Exception happened when server tried to get ticket ID from Pull Request - " + e.getMessage(), e);
        }

        throw new TicketNotFoundException("JIRA ticket can't be found - " +
                "PR title \"" + (pr == null ? "" : pr.getTitle()) + "\" should starts with \"" + ticketPrefix + "NNNNN\"." +
                " Please, rename PR according to the" +
                " <a href='https://cwiki.apache.org/confluence/display/IGNITE/How+to+Contribute" +
                "#HowtoContribute-1.CreateGitHubpull-request'>contributing guide</a>" +
                " or use branch name according ticket name.");
    }

    @Nullable public PullRequest findPrForBranch(
            @Nullable @QueryParam("serverId") String srvId,
            @Nullable @QueryParam("branchName") String branchForTc) {
        Integer prId = IGitHubConnection.convertBranchToPrId(branchForTc);

        if (prId == null)
            return null;

        return gitHubProvider.server(srvId).getPullRequest(prId);
    }



    /**
     * @param branchForTc Branch for tc.
     * @param ticketPrefix JIRA Ticket prefix.
     * @param gitHubIgn GitHub connection ign.
     */
    @Nullable
    private static String prLessTicket(String branchForTc,
                                       String ticketPrefix,
                                       IGitHubConfig gitHubIgn) {
        String branchPrefix = gitHubIgn.gitBranchPrefix();

        if (!branchForTc.startsWith(branchPrefix))
            return null;

        try {
            int ticketNum = Integer.parseInt(branchForTc.substring(branchPrefix.length()));

            return ticketPrefix + ticketNum;
        }
        catch (NumberFormatException ignored) {
        }
        return null;
    }
}
