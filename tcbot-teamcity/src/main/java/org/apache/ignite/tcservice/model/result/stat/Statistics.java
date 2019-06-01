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

package org.apache.ignite.tcservice.model.result.stat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.tcservice.model.conf.bt.Parameters;

/**
 * Build statistics reported by TC, data is stored in properties
 */
@XmlRootElement(name = "properties")
@XmlAccessorType(XmlAccessType.FIELD)
public class Statistics extends Parameters {
    /** The build duration (all build stages). */
    public static final String BUILD_DURATION = "BuildDuration";

    /** Build duration self time: The build steps duration (excluding the checkout and artifact publishing time, etc.) */
    public static final String BUILD_DURATION_NET_TIME = "BuildDurationNetTime";

    /** Build stage duration sources update. */
    public static final String SOURCES_UPDATE_DURATION = "buildStageDuration:sourcesUpdate";

    /** Artifacts publishing. */
    public static final String ARTIFACTS_PUBLISHING_DURATION = "buildStageDuration:artifactsPublishing";

    /** Dependecies resolving. */
    public static final String DEPENDECIES_RESOLVING_DURATION = "buildStageDuration:dependenciesResolving";
}
