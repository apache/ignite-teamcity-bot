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

package org.apache.ignite.tcservice.model.result;

import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/** Running build progress information from TeamCity. */
@XmlRootElement(name = "running-info")
@XmlAccessorType(XmlAccessType.FIELD)
public class ProgressInfo {
    /** Current completion rate, percent. */
    @Nullable @XmlAttribute public Integer percentageComplete;

    /** Seconds passed since build start. */
    @Nullable @XmlAttribute public Long elapsedSeconds;

    /** Estimated total build duration in seconds. */
    @Nullable @XmlAttribute public Long estimatedTotalSeconds;

    /** Estimated remaining build time in seconds. */
    @Nullable @XmlAttribute public Long leftSeconds;

    /** Current TeamCity stage text. */
    @Nullable @XmlAttribute public String currentStageText;

    /** Whether TeamCity considers this build outdated. */
    @Nullable @XmlAttribute public Boolean outdated;

    /** Whether TeamCity suspects the build is hanging. */
    @Nullable @XmlAttribute public Boolean probablyHanging;

    /** Latest build log activity time. */
    @Nullable @XmlAttribute public String lastActivityTime;
}
