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

package org.apache.ignite.tcbot.common.conf;

/**
 * Apache Ignite Teamcity Bot properties.
 */
public class TcBotSystemProperties {
    /** Dev mode. */
    public static final String DEV_MODE = "DEV_MODE";

    /** Teamcity bot recorder. */
    public static final String TEAMCITY_BOT_RECORDER = "teamcity.bot.recorder";

    /**
     * Teamcity bot data storage configuration region size in gigabytes. Default is 20% of physical RAM.
     */
    public static final String TEAMCITY_BOT_REGIONSIZE = "teamcity.bot.regionsize";

    /** System property to specify: Teamcity helper home. Ignite home will be set to same dir. */
    public static final String TEAMCITY_HELPER_HOME = "teamcity.helper.home";
}
