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

package org.apache.ignite.ci.analysis;

import org.apache.ignite.ci.db.Persisted;

/**
 * Created by dpavlov on 09.08.2017.
 */
@Persisted
public class SuiteInBranch implements Comparable<SuiteInBranch> {
    public String id;
    public String branch;

    public SuiteInBranch(String id, String branch) {
        this.id = id;
        this.branch = branch;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        SuiteInBranch id = (SuiteInBranch)o;

        if (this.id != null ? !this.id.equals(id.id) : id.id != null)
            return false;
        return branch != null ? branch.equals(id.branch) : id.branch == null;
    }

    @Override public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (branch != null ? branch.hashCode() : 0);
        return result;
    }

    @Override public int compareTo(SuiteInBranch o) {
        int runConfCompare = id.compareTo(o.id);
        if (runConfCompare != 0)
            return runConfCompare;
        return branch.compareTo(o.branch);
    }

    @Override public String toString() {
        return "{" +
            "id='" + id + '\'' +
            ", branch='" + branch + '\'' +
            '}';
    }

    public String getBranch() {
        return branch;
    }

    public String getSuiteId() {
        return id;
    }
}
