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
package org.apache.ignite.ci.tcbot;

import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.web.model.Version;
import org.apache.ignite.lang.IgniteProductVersion;

/**
 * Service for general requests processing, which are not related to builds/JIRA/GitHub.
 */
public class TcBotGeneralService {
    /** Ignite provider. */
    @Inject Provider<Ignite> igniteProvider;

    /**
     *
     */
    public Version version() {
        Version ver = new Version();

        try {
            IgniteProductVersion ignProdVer = igniteProvider.get().version();

            ver.ignVer = ignProdVer.major() + "." + ignProdVer.minor() + "." + ignProdVer.maintenance();

            ver.ignVerFull = ignProdVer.toString();
        }
        catch (Exception ignored) {
            //probably service is starting
            ver.ignVer = "?";
        }

        return ver;
    }
}
