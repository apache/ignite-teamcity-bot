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

package org.apache.ignite.ci.tcbot.jira.v3.adf;

public class Mark extends Element {
    private static final String STRONG = "strong";
    private static final String COLOR = "textColor";
    private static final String LINK = "link";

    private Attribute attrs;

    private Mark(String type) {
        super(type);
    }

    private Mark(String type, Attribute attrs) {
        super(type);

        this.attrs = attrs;
    }

    /**
     * @link https://developer.atlassian.com/cloud/jira/platform/apis/document/marks/strong/
     */
    public static Mark textStrong() {
        return new Mark(STRONG);
    }

    /**
     * @link https://developer.atlassian.com/cloud/jira/platform/apis/document/marks/textColor/
     */
    public static Mark textColor(String color) {
        return new Mark(COLOR, new ColorAttribute(color));
    }

    /**
     * @link https://developer.atlassian.com/cloud/jira/platform/apis/document/marks/link/
     */
    public static Mark textLink(String link) {
        return new Mark(LINK, new LinkAttribute(link));
    }

    interface Attribute {
    }

    static class ColorAttribute implements Attribute {
        String color;

        ColorAttribute(String color) {
            this.color = color;
        }
    }

    static class LinkAttribute implements Attribute {
        String href;

        LinkAttribute(String href) {
            this.href = href;
        }
    }
}
