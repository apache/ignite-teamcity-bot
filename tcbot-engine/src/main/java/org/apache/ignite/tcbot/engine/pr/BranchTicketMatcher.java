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

package org.apache.ignite.tcbot.engine.pr;

import com.google.common.base.Strings;
import java.util.Collection;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.githubignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.githubservice.IGitHubConnection;
import org.apache.ignite.jiraignited.IJiraIgnitedProvider;
import org.apache.ignite.jiraservice.Ticket;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcbot.common.interceptor.GuavaCached;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;

/**
 *
 */
public class BranchTicketMatcher {
    /** Config. */
    @Inject private ITcBotConfig cfg;

    /** GitHub connection ignited provider. */
    @Inject private IGitHubConnIgnitedProvider gitHubProvider;

    /** JIRA provider */
    @Inject private IJiraIgnitedProvider jiraIgnProv;

    @Nullable public String resolveTcBranchForPrLess(Ticket ticket,
        IJiraServerConfig jiraCfg,
        IGitHubConfig gitHubCfg) {
        String branchNumPrefix = jiraCfg.branchNumPrefix();

        if (Strings.isNullOrEmpty(branchNumPrefix)) {
            //an easy way, no special branch and ticket mappings specified, use project code.
            int ticketId = ticket.keyWithoutProject(jiraCfg.projectCodeForVisa());

            return gitHubCfg.gitBranchPrefix() + ticketId;
        }

        String branchJiraIdentification = findFixPrefixedNoInValues(branchNumPrefix,
            ticket.key,
            ticket.fields.summary,
            ticket.fields.customfield_11050);

        return convertJiraToGit(branchJiraIdentification, branchNumPrefix, gitHubCfg);

    }

    /**
     * Converts JIRA notation branch name to actual git branch name. Usually it is just lower-casing, but any mapping
     * may be configured.
     *
     * @param branchJiraIdentification Branch jira identification.
     * @param branchNumPrefix Branch number prefix.
     * @param gitHubCfg GH connection config.
     */
    private String convertJiraToGit(String branchJiraIdentification,
        String branchNumPrefix,
        IGitHubConfig gitHubCfg) {
        if (Strings.isNullOrEmpty(branchJiraIdentification))
            return null;

        return gitHubCfg.gitBranchPrefix() + branchJiraIdentification.substring(branchNumPrefix.length());
    }

    /**
     * @param tickets Tickets.
     * @param jiraCfg Jira config.
     * @param prTitle
     */
    @Nullable public Ticket resolveTicketIdForPrBasedContrib(Collection<Ticket> tickets,
        IJiraServerConfig jiraCfg, String prTitle) {
        String branchNumPrefix = jiraCfg.branchNumPrefix();

        if (Strings.isNullOrEmpty(branchNumPrefix)) {
            //an easy way, no special branch and ticket mappings specified, use project code.
            String jiraPrefix = jiraCfg.projectCodeForVisa() + Ticket.PROJECT_DELIM;

            final String ticketKey = findFixPrefixedNumber(prTitle, jiraPrefix);

            return tickets.stream()
                .filter(t -> Objects.equals(t.key, ticketKey))
                .findFirst()
                .orElseGet(() -> new Ticket(ticketKey));
        }

        String branchNum = findFixPrefixedNumber(prTitle, branchNumPrefix);

        if (branchNum == null) // PR does not mention
            return null;

        return findTicketMentions(tickets, branchNum);
    }

    /**
     * @param srvCode Server code.
     * @param branchNum Branch number to be checked.
     */
    @SuppressWarnings("WeakerAccess")
    @GuavaCached(maximumSize = 3000, expireAfterWriteSecs = 60, cacheNullRval = true)
    @Nullable
    protected Ticket findTicketMentions(String srvCode, @Nullable String branchNum) {
        return findTicketMentions(jiraIgnProv.server(srvCode).getTickets(), branchNum);
    }

    /**
     * @param tickets Tickets.
     * @param branchNum Branch number to be checked.
     */
    @Nullable private Ticket findTicketMentions(Collection<Ticket> tickets, @Nullable String branchNum) {
        if (Strings.isNullOrEmpty(branchNum))
            return null;

        return tickets.stream()
            .filter(t -> Objects.equals(t.key, branchNum))
            .findFirst()
            .orElseGet(() -> findTicketMentionsInSupplementaryFields(tickets, branchNum));
    }

    /**
     * @param tickets Tickets.
     * @param branchNum Branch number to be checked.
     */
    @Nullable private Ticket findTicketMentionsInSupplementaryFields(Collection<Ticket> tickets, String branchNum) {
        if (Strings.isNullOrEmpty(branchNum))
            return null;

        return tickets.stream()
            .filter(t -> mentionsBranch(branchNum, t))
            .findFirst()
            .orElse(null);
    }

    /**
     * @param branchName Full branch name in jira.
     * @param ticket Ticket.
     */
    private boolean mentionsBranch(String branchName, Ticket ticket) {
        String summary = ticket.fields.summary;
        if (summary != null && summary.contains(branchName))
            return true;

        String val = ticket.fields.customfield_11050;
        if (val != null && val.contains(branchName))
            return true;

        return false;
    }

    @Nullable private String findFixPrefixedNoInValues(@Nonnull String prefix, String... values) {
        for (String value : values) {
            String fixPrefixedNum = findFixPrefixedNumber(value, prefix);

            if (fixPrefixedNum != null)
                return fixPrefixedNum;
        }
        return null;
    }

    /**
     * @param val Pull Request/Ticket title prefix or other text to find constant-prefix text.
     * @param prefix Ticket prefix.
     * @return Branch number or null.
     */
    @Nullable private String findFixPrefixedNumber(@Nullable String val, @Nonnull String prefix) {
        if (Strings.isNullOrEmpty(val))
            return null;

        int idxOfBranchNum = val.toUpperCase().indexOf(prefix.toUpperCase());

        if (idxOfBranchNum < 0)
            return null;

        int beginIdx = prefix.length() + idxOfBranchNum;
        int endIdx = beginIdx;

        while (endIdx < val.length() && Character.isDigit(val.charAt(endIdx)))
            endIdx++;

        if (endIdx == beginIdx || endIdx - beginIdx <= 1) // protection from one digit resolution to IGNITE-2
            return null;

        return prefix + val.substring(beginIdx, endIdx);
    }

    /**
     *
     */
    public static class TicketNotFoundException extends Exception {
        TicketNotFoundException(String msg) {
            super(msg);
        }

        TicketNotFoundException(String msg, Exception e) {
            super(msg, e);
        }
    }

    public String resolveTicketFromBranch(String srvCode, String ticketFullName,
        String branchForTc) throws TicketNotFoundException {
        if (!Strings.isNullOrEmpty(ticketFullName))
            return ticketFullName; //old code probably not needed now; ticketFullName = ticketFullName.toUpperCase().startsWith(prefix) ? ticketFullName : prefix + ticketFullName;

        IJiraServerConfig jiraCfg = cfg.getJiraConfig(srvCode);
        IGitHubConfig gitCfg = cfg.getGitConfig(srvCode);

        PullRequest pr; // filled only when special PR found

        String ticketPrefix;
        try {
            String branchNumPrefix = jiraCfg.branchNumPrefix();

            ticketPrefix = Strings.isNullOrEmpty(branchNumPrefix)
                ? jiraCfg.projectCodeForVisa() + Ticket.PROJECT_DELIM
                : branchNumPrefix;

            String prLessTicket = prLessTicket(branchForTc, ticketPrefix, gitCfg);
            if (!Strings.isNullOrEmpty(prLessTicket)) {
                if (Strings.isNullOrEmpty(branchNumPrefix)) {
                    //Default, simple case

                    return prLessTicket; //find out PRless ticket,
                }
                else {
                    // PR less ticket only mentioned in real ticket
                    Ticket ticket = findTicketMentions(srvCode, prLessTicket);

                    if (ticket != null && !Strings.isNullOrEmpty(ticket.key))
                        return ticket.key; // found real JIRA ticket for comment
                }
            }

            pr = findPrForBranch(srvCode, branchForTc);
            if (pr != null) {
                String jiraPrefixInPr = findFixPrefixedNumber(pr.getTitle(), ticketPrefix);

                String ticketFromPr;
                if (Strings.isNullOrEmpty(branchNumPrefix)) {
                    //Default, simple case, branch name matching gives us a ticket
                    ticketFromPr = jiraPrefixInPr;
                }
                else {
                    if (Strings.isNullOrEmpty(jiraPrefixInPr))
                        ticketFromPr = null;
                    else {
                        Ticket ticketMentions = findTicketMentions(srvCode, jiraPrefixInPr);

                        ticketFromPr = ticketMentions == null ? null : ticketMentions.key;
                    }
                }

                if (!Strings.isNullOrEmpty(ticketFromPr))
                    return ticketFromPr; // found real JIRA ticket for comment
            }
        }
        catch (Exception e) {
            throw new TicketNotFoundException("Exception happened when server tried to get ticket ID from Pull Request - " + e.getMessage(), e);
        }

        throw new TicketNotFoundException("JIRA ticket can't be found - " +
            "PR title \"" + (pr == null ? "" : pr.getTitle()) + "\" should starts with \"" + ticketPrefix + "NNNNN\"." +
            " Please, rename PR according to the" +
            " <a href='https://cwiki.apache.org/confluence/display/IGNITE/How+to+Contribute" +
            "#HowtoContribute-1.CreateGitHubpull-request'>contributing guide</a>" +
            " or use branch name according ticket name.");
    }

    @Nullable
    private PullRequest findPrForBranch(
            @Nullable String srvId,
            @Nullable String branchForTc) {
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
    @Nullable private static String prLessTicket(String branchForTc,
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
