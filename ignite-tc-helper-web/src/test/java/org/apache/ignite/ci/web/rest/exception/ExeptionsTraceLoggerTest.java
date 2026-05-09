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

package org.apache.ignite.ci.web.rest.exception;

import java.net.SocketException;
import javax.ws.rs.core.Response;
import javax.xml.bind.UnmarshalException;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ExeptionsTraceLoggerTest {
    /** */
    @Test
    public void responseContainsRootCauseFromJaxbLinkedException() {
        SocketException socketException = new SocketException("Connection reset");
        UnmarshalException unmarshalException = new UnmarshalException("Failed to parse TeamCity XML", socketException);
        RuntimeException exception = new RuntimeException("TeamCity request failed", unmarshalException);

        Response response = new ExeptionsTraceLogger().toResponse(exception);

        String msg = (String)response.getEntity();

        assertTrue(msg.contains("Internal Server Error [500]."));
        assertTrue(msg.contains("Reason: SocketException: Connection reset"));
        assertTrue(msg.contains("RuntimeException: TeamCity request failed"));
        assertTrue(msg.contains("UnmarshalException: Failed to parse TeamCity XML"));
    }
}
