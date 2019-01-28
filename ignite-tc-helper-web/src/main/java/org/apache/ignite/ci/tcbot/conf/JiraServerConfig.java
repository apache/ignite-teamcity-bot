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

/**
 *
 */
public class JiraServerConfig implements IJiraServerConfig {
    private String name;

    /**
     * Tickets for commenting in JIRA and finding out PA tickets.
     */
    private String projectCode;

    private String branchTicketTemplate;
    /** Branch ticket template search fields, list of JIRA fields IDs to be checked for finding out branch. */
    private ArrayList<String> branchTicketTemplateSearchFields;

    private Properties props;
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
}
