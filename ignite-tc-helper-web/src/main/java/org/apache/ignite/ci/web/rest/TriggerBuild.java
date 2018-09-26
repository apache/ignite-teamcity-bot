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

package org.apache.ignite.ci.web.rest;

import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.exception.ServiceUnauthorizedException;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.internal.util.typedef.F;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Path("build")
@Produces(MediaType.APPLICATION_JSON)
public class TriggerBuild {
    @Context
    private ServletContext context;

    @Context
    private HttpServletRequest req;

    @GET
    @Path("trigger")
    public SimpleResult triggerBuild(
        @Nullable @QueryParam("serverId") String srvId,
        @Nullable @QueryParam("branchName") String branchForTc,
        @Nullable @QueryParam("suiteId") String suiteId,
        @Nullable @QueryParam("top") Boolean top,
        @Nullable @QueryParam("observe") Boolean observe,
        @Nullable @QueryParam("ticketId") String ticketId
    ) {
        String jiraRes = "";
        final ICredentialsProv prov = ICredentialsProv.get(req);

        if (!prov.hasAccess(srvId))
            throw ServiceUnauthorizedException.noCreds(srvId);

        ITcHelper helper = CtxListener.getTcHelper(context);

        final ITeamcity teamcity = helper.server(srvId, prov);

        Build build = teamcity.triggerBuild(suiteId, branchForTc, false, top != null && top);

        if (observe != null && observe)
            jiraRes = observeJira(srvId, branchForTc, ticketId, helper, teamcity, build, prov);

        return new SimpleResult("Tests started." + (!jiraRes.isEmpty() ? "<br>" + jiraRes : ""));
    }

    @GET
    @Path("commentJira")
    public SimpleResult commentJira(
        @Nullable @QueryParam("serverId") String srvId,
        @Nullable @QueryParam("branchName") String branchForTc,
        @Nullable @QueryParam("suiteId") String suiteId,
        @Nullable @QueryParam("ticketId") String ticketId
    ) {
        try {
            return commentJiraEx(srvId, branchForTc, suiteId, ticketId);
        } catch (Exception e) {
            e.printStackTrace();

            //todo better exception handling at jersey level

            throw e;
        }
    }

    @NotNull
    public SimpleResult commentJiraEx(@QueryParam("serverId") @Nullable String srvId, @QueryParam("branchName") @Nullable String branchForTc, @QueryParam("suiteId") @Nullable String suiteId, @QueryParam("ticketId") @Nullable String ticketId) {
        final ICredentialsProv prov = ICredentialsProv.get(req);

        if (!prov.hasAccess(srvId))
            throw ServiceUnauthorizedException.noCreds(srvId);

        ITcHelper helper = CtxListener.getTcHelper(context);
        String jiraRes = "";

        final ITeamcity teamcity = helper.server(srvId, prov);

        if (Strings.isNullOrEmpty(ticketId)) {
            PullRequest pr = teamcity.getPullRequest(branchForTc);

            ticketId = getTicketId(pr);

            if (ticketId.isEmpty()) {
                jiraRes = "JIRA ticket can't be commented - " +
                    "PR title \"" + pr.getTitle() + "\" should starts with \"IGNITE-XXXX\"." +
                    " Please, rename PR according to the" +
                    " <a href='https://cwiki.apache.org/confluence/display/IGNITE/How+to+Contribute" +
                    "#HowtoContribute-1.CreateGitHubpull-request'>contributing guide</a>" +
                    " or enter ticket id in the form.";
            }

        }

        if (helper.notifyJira(srvId, prov, suiteId, branchForTc, "ignite-" + ticketId))
            return new SimpleResult("JIRA commented." + (!jiraRes.isEmpty() ? jiraRes : ""));
        else
            // TODO Write catched exceptions to the response.
            return new SimpleResult("JIRA wasn't commented." + (!jiraRes.isEmpty() ? "<br>" + jiraRes : ""));
    }

    /**
     * @param srvId Server id.
     * @param branchForTc Branch for TeamCity.
     * @param ticketId JIRA ticket number.
     * @param helper Helper.
     * @param teamcity TeamCity.
     * @param build Build.
     * @param prov Credentials.
     * @return Message with result.
     */
    private String observeJira(
        String srvId,
        String branchForTc,
        @Nullable String ticketId,
        ITcHelper helper,
        ITeamcity teamcity,
        Build build,
        ICredentialsProv prov
    ) {
        if (F.isEmpty(ticketId)) {
            PullRequest pr = teamcity.getPullRequest(branchForTc);

            ticketId = getTicketId(pr);

            if (ticketId.isEmpty()) {
                return "JIRA ticket will not be notified after the tests are completed - " +
                    "PR title \"" + pr.getTitle() + "\" should starts with \"IGNITE-XXXX\"." +
                    " Please, rename PR according to the" +
                    " <a href='https://cwiki.apache.org/confluence/display/IGNITE/How+to+Contribute" +
                    "#HowtoContribute-1.CreateGitHubpull-request'>contributing guide</a>.";
            }
        }

        BuildObserver observer = CtxListener.getInjector(context).getInstance(BuildObserver.class);

        observer.observe(build, srvId, prov, "ignite-" + ticketId);

        return "JIRA ticket IGNITE-" + ticketId + " will be notified after the tests are completed.";
    }

    /**
     * @param pr Pull Request.
     * @return JIRA ticket number.
     */
    @NotNull private String getTicketId(PullRequest pr) {
        String ticketId = "";

        if (pr.getTitle().startsWith("IGNITE-")) {
            int beginIdx = 7;
            int endIdx = 7;

            while (endIdx < pr.getTitle().length() && Character.isDigit(pr.getTitle().charAt(endIdx)))
                endIdx++;

            ticketId = pr.getTitle().substring(beginIdx, endIdx);
        }

        return ticketId;
    }

    @GET
    @Path("triggerBuilds")
    public SimpleResult triggerBuilds(
        @Nullable @QueryParam("serverId") String serverId,
        @Nullable @QueryParam("branchName") String branchName,
        @NotNull @QueryParam("suiteIdList") String suiteIdList,
        @Nullable @QueryParam("top") Boolean top) {

        final ICredentialsProv prov = ICredentialsProv.get(req);

        if (!prov.hasAccess(serverId))
            throw ServiceUnauthorizedException.noCreds(serverId);

        List<String> strings = Arrays.asList(suiteIdList.split(","));
        if (strings.isEmpty())
            return new SimpleResult("Error: nothing to run");

        final ITeamcity helper = CtxListener.getTcHelper(context).server(serverId, prov);

        boolean queueToTop = top != null && top;

        for (String suiteId : strings) {
            System.out.println("Triggering [ " + suiteId + "," + branchName + "," + "top=" + queueToTop + "]");

            helper.triggerBuild(suiteId, branchName, false, queueToTop);
        }

        return new SimpleResult("OK");
    }

}
