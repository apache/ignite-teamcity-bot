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

package org.apache.ignite.ci.tcbot.common;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import org.apache.ignite.tcbot.persistence.Persisted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.Snappy;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Field is included into bigger entries, so it is placed in backward compatible package.
 */
@Persisted
public class StringFieldCompacted {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(StringFieldCompacted.class);
    public static final int FLAG_UNCOMPRESSED = 0;
    public static final int FLAG_SNAPPY = 1;
    public static final int FLAG_GZIP = 2;
    byte flag;
    byte data[];

    public StringFieldCompacted() {

    }

    public StringFieldCompacted(String value) {
        setValue(value);
    }

    public String getValue() {
        if (data == null)
            return "";

        if (flag == FLAG_SNAPPY) {
            try {
                return new String(Snappy.uncompress(data), StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                logger.error("Snappy.uncompress failed: " + e.getMessage(), e);
                return null;
            }
        }
        else if (flag == FLAG_UNCOMPRESSED)
            return new String(data, StandardCharsets.UTF_8);
        else if (flag == FLAG_GZIP) {
            try {
                return unzipToString(data);
            }
            catch (Exception e) {
                logger.error("GZip.uncompress failed: " + e.getMessage(), e);
                return null;
            }
        }
        else
            return null;
    }

    @Nonnull
    public static String unzipToString(byte[] data) throws IOException {
        final ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (final GZIPInputStream gzi = new GZIPInputStream(in)) {
            byte[] outbuf = new byte[data.length];
            int len;
            while ((len = gzi.read(outbuf, 0, outbuf.length)) != -1)
                bos.write(outbuf, 0, len);
        }

        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public void setValue(String str) {
        if (Strings.isNullOrEmpty(str)) {
            this.data = null;
            return;
        }

        byte[] uncompressed;
        byte[] snappy = null;
        byte[] gzip = null;
        try {
            uncompressed = str.getBytes(StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            logger.error("Set details failed: " + e.getMessage(), e);
            return;
        }

        try {
            snappy = Snappy.compress(uncompressed);
        }
        catch (Exception e) {
            logger.error("Snappy.compress failed: " + e.getMessage(), e);
        }

        try {
            gzip = zipBytes(uncompressed);
        }
        catch (Exception e) {
            logger.error("Snappy.compress failed: " + e.getMessage(), e);
        }

        final int snappyLen = snappy != null ? snappy.length : -1;
        final int gzipLen = gzip != null ? gzip.length : -1;

        flag = FLAG_UNCOMPRESSED;
        //uncompressed
        data = uncompressed;

        if (snappyLen > 0 && snappyLen < data.length) {
            flag = FLAG_SNAPPY;
            data = snappy;
        }

        if (gzipLen > 0 && gzipLen < data.length) {
            flag = FLAG_GZIP;
            data = gzip;
        }

        logger.debug("U " + uncompressed.length + " S " + snappyLen + " Z " + gzipLen + ": F (" +
            flag + ")");
    }

    public static byte[] zipBytes(byte[] uncompressed) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(out)) {
            gzipOutputStream.write(uncompressed);
        }

        return out.toByteArray();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        StringFieldCompacted compacted = (StringFieldCompacted)o;
        return flag == compacted.flag &&
            Arrays.equals(data, compacted.data);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(flag, data);
    }

    public boolean isFilled() {
        return data != null;
    }
}
