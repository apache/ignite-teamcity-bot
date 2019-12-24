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

import java.time.format.DateTimeFormatter;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.annotation.Nullable;
import java.util.Collection; 

/**
 * Teamcity Server configuration.
 */
public interface ITcServerConfig {
    /**
     * Default tracked branch in case it was not specified.
     */
    String DEFAULT_TRACKED_BRANCH_NAME = "master";

    /**
     * @return Another TC Server (service) config name to use settings from. Filled only for server aliases.
     */
    @Nullable
    public String reference();

    /**
     * @return ID of additional server (service code), that user should have access to.
     */
    @Nullable public String additionalServiceToCheckAccess();

    /**
     * @return Normalized Host address, ends with '/'.
     */
    @NonNull public String host();

    /**
     * @return Directory for downloading build logs (will contain ZIP files).
     */
    @NonNull public String logsDirectory();

    /**
     * @return internal naming of default tracked branch for this server.
     */
    @NonNull public String defaultTrackedBranch();

    /**
     * Suite id to be used for VISAs (to avoid search using defaultTrackedBranch())
     */
    @Nullable public String defaultVisaSuiteId();

    /**
     * Provides build parameters, whichi could be used for filtering builds in RunHist/Invocations and tagging in UI.
     *
     * @return set of build parameters specifications.
     */
    @NonNull public Collection<? extends IBuildParameterSpec> filteringParameters();

    /**
     * @return set of suite codes (build type IDs), failures in which should be threated as critical and notified.
     */
    @NonNull public Collection<String> trustedSuites();

    /**
     * @return Time as auto-triggering build is disabled. {@link DateTimeFormatter.ISO_LOCAL_TIME} must be used.
     */
    @Nullable String autoTriggeringBuildDisabledStartTime();

    /**
     * @return Time as auto-triggering build is enabled. {@link DateTimeFormatter.ISO_LOCAL_TIME} must be used.
     */
    @Nullable String autoTriggeringBuildDisabledEndTime();
}
