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

package org.apache.ignite.jiraservice.adapters;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.apache.ignite.tcbot.common.conf.JiraApiVersion;

public class TypeFactory<T> implements TypeAdapterFactory {
    private final JiraApiVersion apiVersion;

    private final Class<T> base;

    private final Class<? extends T> v2Class;
    private final Class<? extends T> v3Class;

    public TypeFactory(JiraApiVersion apiVersion, Class<T> base, Class<? extends T> v2, Class<? extends T> v3) {
        this.apiVersion = apiVersion;
        this.base = base;
        this.v2Class = v2;
        this.v3Class = v3;
    }

    @Override public <R> TypeAdapter<R> create(Gson gson, TypeToken<R> type) {
        if (type == null)
            return null;

        Class<?> rawType = type.getRawType();

        boolean handle = base.isAssignableFrom(rawType);

        if (!handle)
            return null;

        TypeAdapter<?> delegate;
        switch (apiVersion) {
            case V2:
                delegate = gson.getDelegateAdapter(this, TypeToken.get(v2Class));
                break;
            case V3:
                delegate = gson.getDelegateAdapter(this, TypeToken.get(v3Class));
                break;
            default:
                throw new IllegalArgumentException("Unknown jira api version [ver=" + apiVersion + ']');
        }

        return new TypeAdapter<R>() {
            @Override public void write(JsonWriter out, R value) throws IOException {
                ((TypeAdapter<R>) delegate).write(out, value);
            }
            @Override public R read(JsonReader reader) throws IOException {
                return ((TypeAdapter<R>) delegate).read(reader);
            }
        }.nullSafe();
    }
}
