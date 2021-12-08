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
package org.apache.ignite.tcbot.engine.conf;

import javax.annotation.Nullable;

public class BranchTrackedPersisted extends BranchTracked {
    @Nullable
    protected Boolean softDeleted;

    public static BranchTrackedPersisted initFrom(ITrackedBranch b) {
        BranchTrackedPersisted bp = new BranchTrackedPersisted();

        b.chainsStream().map(ChainAtServerTracked::initFrom).forEach(bp.chains::add);

        bp.id = b.name();
        bp.disableIssueTypes = b.disableIssueTypes();
        bp.softDeleted = false;

        return bp;
    }

    public boolean isDeleted() {
        return softDeleted != null && softDeleted;
    }

    public boolean isAlive() {
        return !isDeleted();
    }

}
