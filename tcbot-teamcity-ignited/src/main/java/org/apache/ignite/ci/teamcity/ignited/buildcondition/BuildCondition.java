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

package org.apache.ignite.ci.teamcity.ignited.buildcondition;

import java.util.Date;
import java.util.Objects;

/**
 * Mark build as valid or invalid.
 */
public class BuildCondition {
    /** Build id. */
    public int buildId;

    /** Username. */
    public String username;

    /** Is valid. */
    public boolean isValid;

    /** Date. */
    public Date date;

    /** Field, where build was marked. */
    public String field;

    /**
     * Default constructor.
     */
    public BuildCondition(){}

    /**
     * @param buildId Build id.
     * @param username Username.
     * @param isValid Is valid.
     * @param field Field.
     */
    public BuildCondition(int buildId, String username, boolean isValid, String field) {
        this.buildId = buildId;
        this.username = username;
        this.isValid = isValid;
        this.date = new Date();
        this.field = field;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return "BuildCondition{" +
            "buildId=" + buildId +
            ", username='" + username + '\'' +
            ", isValid=" + isValid +
            ", date=" + date +
            ", field='" + field + '\'' +
            '}';
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof BuildCondition))
            return false;

        BuildCondition that = (BuildCondition)o;

        return buildId == that.buildId &&
            isValid == that.isValid &&
            Objects.equals(username, that.username);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(buildId, username, isValid);
    }
}
