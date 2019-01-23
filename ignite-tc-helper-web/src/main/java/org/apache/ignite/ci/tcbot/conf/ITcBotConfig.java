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

import java.util.Collection;
import java.util.List;
import org.apache.ignite.ci.conf.BranchesTracked;

/**
 * Teamcity Bot configuration access inteface.
 */
public interface ITcBotConfig {
    /** Default server id. */
    String DEFAULT_SERVER_ID = "apache";


    public String primaryServerId();

    /**
     * @return Tracked branches configuration for TC Bot.
     */
    public BranchesTracked getTrackedBranches();

    /**
     * @return list of internal TC Bot identifiers of all tracked branches.
     */
    public default List<String> getTrackedBranchesIds() {
        return getTrackedBranches().getIds();
    }

    /**
     * @return list of servers (services) identifiers involved into tracked branhes processing.
     */
    public default Collection<String> getServerIds() {
        return getTrackedBranches().getServerIds();
    }


}
