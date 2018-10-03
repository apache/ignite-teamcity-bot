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
package org.apache.ignite.ci.teamcity;

import org.apache.ignite.ci.IgniteTeamcityConnection;

/**
 * Factory for non-guice creation of TC Connection instance.
 */
public class TcConnectionStaticLinker {
    /**
     * @param srv Server ID.
     */
    public static IgniteTeamcityConnection create(String srv) {
        final IgniteTeamcityConnection conn = new IgniteTeamcityConnection();

        conn.setHttpConn(new TeamcityRecordingConnection());
        conn.init(srv);

        return conn;
    }
}
