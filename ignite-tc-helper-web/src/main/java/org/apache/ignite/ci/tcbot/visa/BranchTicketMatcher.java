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
import java.util.List;
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

            return findFixPrefixedNumber(pr, jiraPrefix);
        }

        String prTitle = pr.getTitle();

        String branchNum = findFixPrefixedNumber(prTitle, branchNumPrefix);

        if (branchNum == null) // PR does not mention
            return null;

        return findTicketMentions(tickets, branchNum);
    }

    private String findTicketMentions(Collection<Ticket> tickets, String branchNum) {
        return tickets.stream()
                .filter(t -> mentionsBranch(branchNum, t))
                .findFirst()
                .map(t -> t.key).orElse(null);
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
     * @param pr Pull Request.
     * @param prefix Ticket prefix.
     * @return Branch number or null.
     */
    @Nullable public static String findFixPrefixedNumber(PullRequest pr, @NotNull String prefix) {

        return findFixPrefixedNumber(pr.getTitle(), prefix);
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

        PullRequest pr = null; // filled only when special PR found

        String ticketPrefix = null;
        try {
            String branchNumPrefix = jiraCfg.branchNumPrefix();

            ticketPrefix = Strings.isNullOrEmpty(branchNumPrefix) ?
                    jiraCfg.projectCodeForVisa() + TicketCompacted.PROJECT_DELIM
                    : branchNumPrefix;

            String prLessTicket = prLessTicket(branchForTc, ticketPrefix, gitConfig);

            if (Strings.isNullOrEmpty(branchNumPrefix)) {
                //Default, simple case, branch name matching gives us a ticket
                if (!Strings.isNullOrEmpty(prLessTicket))
                    return prLessTicket; //find out PRless ticket,

                //Default, simple case, just use PR name
                pr = findPrForBranch(srvCode, branchForTc);

                if (pr != null)
                    ticketFullName = getTicketFullName(pr, ticketPrefix);
            } else {
                // PR less ticket only mentioned in real ticket
                Set<Ticket> tickets = jiraIgnProv.server(srvCode).getTickets();
                String ticket = findTicketMentions(tickets, prLessTicket);

                if(!Strings.isNullOrEmpty(ticket))
                    return ticket;

                pr = findPrForBranch(srvCode, branchForTc);

                if (pr != null) {
                    String jiraBranchNum = findFixPrefixedNumber(pr, branchNumPrefix);

                    ticketFullName = findTicketMentions(tickets, jiraBranchNum);
                }
            }
        } catch (Exception e) {
            throw new TicketNotFoundException("Exception happened when server tried to get ticket ID from Pull Request - " + e.getMessage(), e);
        }

        if (Strings.isNullOrEmpty(ticketFullName)) {
            throw new TicketNotFoundException("JIRA ticket can't be found - " +
                    "PR title \"" + (pr == null ? "" : pr.getTitle()) + "\" should starts with \"" + ticketPrefix + "NNNNN\"." +
                    " Please, rename PR according to the" +
                    " <a href='https://cwiki.apache.org/confluence/display/IGNITE/How+to+Contribute" +
                    "#HowtoContribute-1.CreateGitHubpull-request'>contributing guide</a>" +
                    " or use branch name according ticket name.");
        }

        return ticketFullName;
    }

    /**
     * @param pr Pull Request.
     * @param prefix Ticket prefix.
     * @return JIRA ticket full name or empty string.
     */
    @NotNull public static String getTicketFullName(PullRequest pr, @NotNull String prefix) {
        String ticketId = "";

        if (pr.getTitle().toUpperCase().startsWith(prefix)) {
            int beginIdx = prefix.length();
            int endIdx = prefix.length();

            while (endIdx < pr.getTitle().length() && Character.isDigit(pr.getTitle().charAt(endIdx)))
                endIdx++;

            ticketId = prefix + pr.getTitle().substring(beginIdx, endIdx);
        }

        return ticketId;
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
