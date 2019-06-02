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
package org.apache.ignite.ci.teamcity.restcached;

import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.tcservice.ITeamcity;

import javax.annotation.Nullable;

/**
 * Provides instance to server with appropriate credentials, may cache instances to avoid odd server instances.
 */
public interface ITcServerProvider {
    /**
     * @param srvId Server id.
     * @param prov Prov.
     */
    public ITeamcity server(String srvId, @Nullable ITcBotUserCreds prov);
}
