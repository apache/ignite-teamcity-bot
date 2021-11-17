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

package org.apache.ignite.tcbot.common.conf;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.security.SecureRandom;
import javax.annotation.Nonnull;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import org.apache.ignite.tcbot.common.util.Base64Util;
import org.apache.ignite.tcbot.common.util.CryptUtil;

import static javax.xml.bind.DatatypeConverter.parseHexBinary;
import static javax.xml.bind.DatatypeConverter.printHexBinary;

/**
 * Encoding utility to avoid simply readable password in config.
 */
public class PasswordEncoder {
    private static final int PREF_LEN = 16;
    private static final int POSTF_LEN = 16;
    private static final char CHAR = 'A';

    public static String decode(String encPass) {
        final String clearBlk = printHexBinary(d(parseHexBinary(encPass.trim())));
        final String passBlk = clearBlk.substring(PREF_LEN * 2, clearBlk.length() - POSTF_LEN * 2);
        final String len = passBlk.substring(0, 2);
        final int i = Integer.parseInt(len, 16) - CHAR;
        final String p = passBlk.substring(2);
        return new String(parseHexBinary(p), CryptUtil.CHARSET);
    }

    public static String encode(String pass) {
        byte[] bytes = pass.getBytes(CryptUtil.CHARSET);
        SecureRandom random = new SecureRandom();
        byte[] pref = random.generateSeed(PREF_LEN);
        byte[] suffix = random.generateSeed(POSTF_LEN);
        int length = bytes.length + CHAR;
        if ((length & ~0xFF) != 0)
            throw new IllegalStateException();
        byte[] len = get1bLen(length);
        byte[] data = concat(concat(pref, len), concat(bytes, suffix));
        return DatatypeConverter.printHexBinary(e(data));
    }

    private static byte[] e(byte[] data) {
        return CryptUtil.aesEcbPkcs5PaddedCrypt(k(), data, Cipher.ENCRYPT_MODE);
    }

    private static byte[] d(byte[] data) {
        return CryptUtil.aesEcbPkcs5PaddedCrypt(k(), data, Cipher.DECRYPT_MODE);
    }

    @Nonnull private static SecretKeySpec k() {
        int reqBytes = 128 / 8;
        String ptrn = "Ignite";
        byte[] raw = Strings.repeat(ptrn, reqBytes / ptrn.length() + 1).substring(0, reqBytes).getBytes();
        return new SecretKeySpec(raw, "AES");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);

        return c;
    }

    private static byte[] get1bLen(int length) {
        return parseHexBinary(Strings.padStart(Integer.toHexString(length), 2, '0'));
    }

    public static void main0(String[] args) {
        String pass = "324aadfe23....";
        String encode = encode(pass);
        System.err.println("Encoded: " +
                "\"gitHubConfigs\": [\n" +
                        "    {\n" +
                        "      \"authTok\": \"" +
                             "" + encode +   "\" , \n }\n" +
                "  ],");
        String decode = decode(encode);
        Preconditions.checkState(decode.equals(pass));
    }

    public static void mainEncodeEmailPassword(String[] args) {
        String pass = "Enter Password Here";
        String encode = encode(pass);
        System.err.println("\"notifications\": {\n" +
            "    \"email\": {\n" +
            "      \"pwd\": \"" + encode+ "\",\n" +
            "    }\n" +
            "  } ");
        String decode = decode(encode);
        Preconditions.checkState(decode.equals(pass));
    }

    public static void main1(String[] args) {
        mainEncodeEmailPassword(args);
    }

    public static void main2(String[] args) {
        encodeJiraTok("ignitetcbot", "enterClearPasswordOrTokenForUser");
    }

    public static void encodeJiraTok(String user, String pwd) {
        String tok =  userPwdToToken(user, pwd);
        String encode = encode(tok);
        System.err.println("Encoded: "  + "=" + encode);
        String decode = decode(encode);
        Preconditions.checkState(decode.equals(tok));
    }

    @Nonnull public static String userPwdToToken(String user, String pwd) {
        return Base64Util.encodeUtf8String(user + ":" + pwd);
    }
}
