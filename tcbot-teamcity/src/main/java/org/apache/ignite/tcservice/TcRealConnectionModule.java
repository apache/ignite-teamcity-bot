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
package org.apache.ignite.tcservice;

import com.google.inject.AbstractModule;
import com.google.inject.internal.SingletonScope;
import org.apache.ignite.tcservice.http.ITeamcityHttpConnection;
import org.apache.ignite.tcservice.http.TeamcityRecorder;
import org.apache.ignite.tcservice.http.TeamcityRecordingConnection;
import org.apache.ignite.tcservice.login.ITcLogin;
import org.apache.ignite.tcservice.login.TcLoginImpl;

/**
 * Guice module to setup real connected server and all related implementations.
 */
public class TcRealConnectionModule extends AbstractModule {
    private ITeamcityHttpConnection conn;

    /** {@inheritDoc} */
    @Override protected void configure() {
        if (conn != null)
            bind(ITeamcityHttpConnection.class).toInstance(conn);
        else
            bind(ITeamcityHttpConnection.class).to(TeamcityRecordingConnection.class);

        bind(TeamcityRecorder.class).in(new SingletonScope());
        bind(ITcLogin.class).to(TcLoginImpl.class).in(new SingletonScope());
    }

    public void overrideHttp(ITeamcityHttpConnection conn) {
        this.conn = conn;
    }
}
