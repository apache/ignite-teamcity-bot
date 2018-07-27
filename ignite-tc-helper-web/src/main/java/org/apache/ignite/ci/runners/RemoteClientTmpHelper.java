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

import com.google.common.collect.Lists;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

public class RemoteClientTmpHelper {
    private static void setupDisco(IgniteConfiguration cfg) {
        final TcpDiscoverySpi spi = new TcpDiscoverySpi();
        final int locPort = 54433;
        spi.setLocalPort(locPort);
        spi.setLocalPortRange(1);
        TcpDiscoveryVmIpFinder finder = new TcpDiscoveryVmIpFinder();
        finder.setAddresses(Lists.newArrayList("172.25.5.21:" + locPort));

        spi.setIpFinder(finder);

        cfg.setDiscoverySpi(spi);
    }

    public static void main(String[] args) {
        final IgniteConfiguration cfg = new IgniteConfiguration();

        setupDisco(cfg);

        cfg.setGridLogger(new Slf4jLogger());

        cfg.setClientMode(true);

        final Ignite ignite = Ignition.start(cfg);
        ignite.cluster().active(true);

        IgniteCache<Object, Object> cache1 = ignite.cache(UserAndSessionsStorage.USERS);
        for (Cache.Entry<Object, Object> next : cache1) {
            System.out.println(next.getKey() + ": " + next.getValue());

            if (next.getKey().equals("someusername")) {
                TcHelperUser u = (TcHelperUser)next.getValue();

                u.resetCredentials();

                cache1.put(next.getKey(), u);
            }
        }

        IgniteCache<Object, Object> cache = ignite.cache(IssuesStorage.ISSUES);
        for (Cache.Entry<Object, Object> next : cache) {
            Object key = next.getKey();
            Object value = next.getValue();

            if (key.toString().contains("GridCacheLifecycleAwareSelfTest.testLifecycleAware")) {
                /*boolean remove = cache.remove(key);

                if (remove)
                    System.err.println("Removed issue " + value);*/

                System.err.println("Issue: " + value);
            }
        }

        ignite.close();
    }
}
