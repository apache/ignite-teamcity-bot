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

package org.apache.ignite.ci.tcmodel.result.stat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.tcmodel.conf.bt.Parameters;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Дмитрий on 01.11.2017.
 */
@XmlRootElement(name = "properties")
@XmlAccessorType(XmlAccessType.FIELD)
public class Statistics extends Parameters {

    @Nullable public Long getBuildDuration() {
        String duration = getParameter("BuildDuration");
        if (duration == null)
            return null;
        return Long.parseLong(duration);
    }

    @Nullable public Long getSourceUpdateDuration() {
        String duration = getParameter(  "buildStageDuration:sourcesUpdate");
        if (duration == null)
            return null;
        return Long.parseLong(duration);
    }
}
