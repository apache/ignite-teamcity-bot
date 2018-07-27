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

package org.apache.ignite.ci.runners;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.user.UserSession;

public class ClientTmpHelper {
    public static void main(String[] args) {
        Ignite ignite = TcHelperDb.startClient();

        ignite.cache(IssuesStorage.ISSUES).clear();
        //ignite.cache(UserAndSessionsStorage.USERS).destroy();
        Object dpavlov = ignite.cache(UserAndSessionsStorage.USERS).get("dpavlov");

        IgniteCache<Object, Object> cache = ignite.cache(IssuesStorage.ISSUES);

        cache.forEach(
            issue->{
                Object key = issue.getKey();
                Issue value = (Issue)issue.getValue();
                 // value.addressNotified.clear();

                cache.put(key, value);
            }
        );

        ignite.close();
    }
}
