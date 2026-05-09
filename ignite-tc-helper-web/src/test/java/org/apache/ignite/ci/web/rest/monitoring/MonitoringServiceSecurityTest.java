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
        String html = readFile(monitoringHtml());

        assertTrue(html.contains("escapeHtml(inv.method)"));
        assertTrue(html.contains("escapeHtml(inv.path)"));
        assertTrue(html.contains("escapeHtml(inv.lastRequest)"));
        assertTrue(html.contains("escapeHtml(req.branch)"));
        assertTrue(html.contains("escapeHtml(req.result)"));
        assertTrue(html.contains("String(str == null ? \"\" : str)"));
    }

    @Test
    public void notificationTestControlsAreHiddenForNonAdmins() throws IOException, NoSuchMethodException {
        String html = readFile(monitoringHtml());
        String css = readFile(styleCss());

        assertTrue(html.contains("<div class=\"adminOnly\">"));
        assertTrue(html.contains("testSlackNotification()"));
        assertTrue(html.contains("testEmailNotification()"));
        assertTrue(css.contains(".adminOnly"));
        assertTrue(css.contains("display: none"));

        assertAdminRequired(MonitoringService.class.getMethod("testSlackNotification"));
        assertAdminRequired(MonitoringService.class.getMethod("testEmailNotification", String.class));
    }

    @Test
    public void taskMonitoringBlockIsHiddenForNonAdmins() throws IOException {
        String html = readFile(monitoringHtml());

        assertTrue(html.contains("<div class=\"adminOnly\">\n    Tasks Monitoring Data:"));
        assertTrue(html.contains("rest/monitoring/tasks"));
        assertFalse(html.contains("Application warnings/errors are available for bot admins."));
    }

    @Test
    public void monitoringPageContainsUserAdminLinkBlock() throws IOException {
        String html = readFile(monitoringHtml());

        assertTrue(html.contains("renderUserAdminLink(result, \"#userAdminBlock\")"));
        assertTrue(html.contains("id=\"userAdminBlock\" style=\"display: none\""));
        assertTrue(html.contains("<b>Users:</b>"));
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

    private static Path styleCss() {
        Path projectPath = Paths.get("src/main/webapp/css/style-1.5.css");

        if (Files.exists(projectPath))
            return projectPath;

        return Paths.get("ignite-tc-helper-web/src/main/webapp/css/style-1.5.css");
    }

    private static String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n');
    }
}
