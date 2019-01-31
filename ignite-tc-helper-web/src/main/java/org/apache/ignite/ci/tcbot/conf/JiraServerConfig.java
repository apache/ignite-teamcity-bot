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
package org.apache.ignite.ci.tcbot.conf;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Properties;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.jira.pure.Fields;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class JiraServerConfig implements IJiraServerConfig {
    /** Service (server) Name. */
    private String name;

    /**
     * Tickets for commenting in JIRA and finding out PA tickets.
     */
    private String projectCode;

    /**
     * Branch number prefix. Optional, if not present {@link #projectCode}-NNNNN is searched.
     * But if branch has different enumeration, this prefix will be searched instead.
     * If specified, that meant tickets maching branches have another identification.
     * For exaple some ticket having ID {@link #projectCode}-N1 will be commented, but a branch will be identified using
     * {@link #branchNumPrefix}N2 with another number.
     *
     * Search of branches will be performed using data in JIRA ticket fields for
     * {@link #projectCode}-N1, fields are listed in {@link #branchNumPrefixSearchFields}.
     */
    private String branchNumPrefix;

    /**
     * Branch ticket template search fields, list of JIRA fields IDs to be checked for finding out branch.
     * Available fields are field names from {@link Fields} class.
     */
    private ArrayList<String> branchNumPrefixSearchFields;

    private Properties props;

    /**
     * JIRA Server URL. HTTPs is highly recommended.
     */
    private String url;

    public JiraServerConfig() {
    }

    public JiraServerConfig(String name, Properties props) {
        this.name = name;
        this.props = props;
    }

    public String getName() {
        return name;
    }

    /**
     * @param props Properties.
     */
    public void properties(Properties props) {
        this.props = props;
    }

    /**
     * @param name Name.
     */
    public void name(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override public String getUrl() {
        if (Strings.isNullOrEmpty(url) && props != null)
            return props.getProperty(HelperConfig.JIRA_URL);

        return url;
    }

    /** {@inheritDoc} */
    @Override public String projectCodeForVisa() {
        if(Strings.isNullOrEmpty(projectCode) && props!=null) {
            String ticketPref = props.getProperty(HelperConfig.JIRA_TICKET_TEMPLATE, "IGNITE-");

            return ticketPref.replaceAll("-", "");
        }

        return projectCode;
    }

    /** {@inheritDoc} */
    @Nullable @Override public String branchNumPrefix() {
        return Strings.emptyToNull(branchNumPrefix);
    }
}
