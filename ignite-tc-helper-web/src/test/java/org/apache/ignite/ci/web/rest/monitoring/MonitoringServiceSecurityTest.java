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

package org.apache.ignite.ci.web.rest.monitoring;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import org.apache.ignite.ci.web.auth.AuthenticationFilter;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MonitoringServiceSecurityTest {
    @Test
    public void requestTimingEndpointsRequireAuthentication() throws NoSuchMethodException {
        assertAuthRequired(MonitoringService.class.getMethod("getRequestStats"));
        assertAuthRequired(MonitoringService.class.getMethod("getRecentRequests"));
        assertAuthRequired(MonitoringService.class.getMethod("resetRequestStats"));
        assertAuthRequired(MonitoringService.class.getMethod("getAiPromptRequests"));
    }

    @Test
    public void logEndpointsRequireAdminRole() throws NoSuchMethodException {
        assertAdminRequired(MonitoringService.class.getMethod("getAppLogSummaryLink"));
        assertAdminRequired(MonitoringService.class.getMethod("getTaskLog", long.class, long.class));
    }

    @Test
    public void mutationEndpointsRequireAdminRole() throws NoSuchMethodException {
        assertAdminRequired(MonitoringService.class.getMethod("resetProfiling"));
        assertAdminRequired(MonitoringService.class.getMethod("resetRequestStats"));
        assertAdminRequired(MonitoringService.class.getMethod("testSlackNotification"));
        assertAdminRequired(MonitoringService.class.getMethod("testEmailNotification", String.class));
    }

    @Test
    public void requestTimingFieldsAreEscaped() throws IOException {
        String html = new String(Files.readAllBytes(monitoringHtml()), StandardCharsets.UTF_8);

        assertTrue(html.contains("escapeHtml(inv.method)"));
        assertTrue(html.contains("escapeHtml(inv.path)"));
        assertTrue(html.contains("escapeHtml(inv.lastRequest)"));
        assertTrue(html.contains("escapeHtml(req.branch)"));
        assertTrue(html.contains("escapeHtml(req.result)"));
        assertTrue(html.contains("String(str == null ? \"\" : str)"));
    }

    private static void assertAuthRequired(Method method) {
        assertFalse(method.isAnnotationPresent(PermitAll.class));
    }

    private static void assertAdminRequired(Method method) {
        RolesAllowed rolesAllowed = method.getAnnotation(RolesAllowed.class);

        assertTrue(rolesAllowed != null);
        assertTrue(java.util.Arrays.asList(rolesAllowed.value()).contains(AuthenticationFilter.ADMIN_ROLE));
    }

    private static Path monitoringHtml() {
        Path projectPath = Paths.get("src/main/webapp/monitoring.html");

        if (Files.exists(projectPath))
            return projectPath;

        return Paths.get("ignite-tc-helper-web/src/main/webapp/monitoring.html");
    }
}
