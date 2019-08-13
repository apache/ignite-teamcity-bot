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
package org.apache.ignite.tcignited.creds;

import com.google.common.base.Strings;

public interface ICredentialsProv {
    String getPrincipalId();

    /**
     * Gets username for particular service
     * @param srvCode Server Id.
     */
    public String getUser(String srvCode);

    /**
     * Gets password for particular service
     * @param srvCode Server Id.
     */
    public String getPassword(String srvCode);

    default boolean hasAccess(String srvCode) {
        return !Strings.isNullOrEmpty(getUser(srvCode)) && !Strings.isNullOrEmpty(getPassword(srvCode));
    }
}
