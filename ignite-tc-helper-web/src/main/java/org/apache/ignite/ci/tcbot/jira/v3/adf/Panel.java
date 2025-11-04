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

/**
 * @link https://developer.atlassian.com/cloud/jira/platform/apis/document/nodes/panel/
 */
public class Panel extends ElementContainer {
    private static final String TYPE = "panel";

    private TypeAttribute attrs;

    public Panel(Type type, String name) {
        super(TYPE);

        Paragraph p = new Paragraph().append(new Text(name, Mark.textStrong()));

        appendInternal(p);

        attrs = new TypeAttribute(type);
    }

    public Panel append(Element element) {
        appendInternal(element);

        return this;
    }

    public enum Type {
        info,
        note,
        warning,
        success,
        error
    }

    static class TypeAttribute {
        String panelType;

        TypeAttribute(Type type) {
            panelType = type.name();
        }
    }
}
