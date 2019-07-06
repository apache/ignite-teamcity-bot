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

package org.apache.ignite.ci.web.model;

/**
 * TC Bot version data.
 */
@SuppressWarnings("PublicField") public class Version {
    /** Default contact. */
    public static final String DEFAULT_CONTACT = "dev@ignite.apache.org";

    /** Github mirror reference. */
    public static final String GITHUB_REF = "https://github.com/apache/ignite-teamcity-bot";

    /** TC Bot Version. */
    public static final String VERSION = "20190706";

    /** Java version, where Web App is running. */
    public String javaVer;

    /** TC Bot Version. */
    public String version = VERSION;

    /** Ignite version. */
    public String ignVer;

    /** Ignite version. */
    public String ignVerFull;

    /** TC Bot GitHub Mirror. */
    public String gitHubMirror = GITHUB_REF;

    /** TC Bot Source */
    public String apacheGitUrl = "https://gitbox.apache.org/repos/asf/ignite-teamcity-bot.git";

    /** Contact email. */
    public String contactEmail = DEFAULT_CONTACT;

    public Version() {
        javaVer = System.getProperty("java.version");
    }
}
