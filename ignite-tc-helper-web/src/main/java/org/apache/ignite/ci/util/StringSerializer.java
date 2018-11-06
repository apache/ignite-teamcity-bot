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

package org.apache.ignite.ci.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;

/**
 *
 */
public class StringSerializer extends StdSerializer<String> implements ContextualSerializer {
    /** */
    private final IStringCompactor strCompactor;

    /** */
    public StringSerializer(IStringCompactor strCompactor) {
        super(String.class);

        this.strCompactor = strCompactor;
    }

    /** */
    @Override public void serialize(String val, JsonGenerator jgen, SerializerProvider provider)
        throws IOException, JsonProcessingException {
        jgen.writeString(Integer.toString(strCompactor.getStringId(val)));
    }

    /** */
    @Override public JsonSerializer<?> createContextual(SerializerProvider prov,
        BeanProperty prop) throws JsonMappingException {
        if (prop.getAnnotation(CompactProperty.class) != null)
            return this;

        return new com.fasterxml.jackson.databind.ser.std.StringSerializer();
    }
}
