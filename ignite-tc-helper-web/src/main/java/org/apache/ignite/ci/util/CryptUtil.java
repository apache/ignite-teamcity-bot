package org.apache.ignite.ci.util;

import jersey.repackaged.com.google.common.base.Throwables;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class CryptUtil {
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    public static byte[] hmacSha256(byte[] keyBytes, String data)   {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(keyBytes, "HmacSHA256");
            sha256_HMAC.init(secret_key);

            return  sha256_HMAC.doFinal(data.getBytes(CHARSET));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw Throwables.propagate(e);
        }
    }

    public static byte[] aesEcbPkcs5PaddedCrypt(byte[] data, int mode, SecretKeySpec key) {
        try {
            final Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(mode, key);
            return cipher.doFinal(data);
        }
        catch (NoSuchAlgorithmException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException e) {
            throw Throwables.propagate(e);
        }
    }

    public static byte[] aesKcv(byte[] userKey) {
        byte[] data = new byte[8];

        return aesEncrypt(userKey, data);
    }

    private static byte[] aesEncrypt(byte[] userKey, byte[] data) {
        SecretKeySpec keySpec = new SecretKeySpec(userKey, "AES");

        return aesEcbPkcs5PaddedCrypt(data, Cipher.ENCRYPT_MODE, keySpec);
    }
}
