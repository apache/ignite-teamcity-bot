package org.apache.ignite.ci.user;

import com.google.common.base.MoreObjects;

import java.util.ArrayList;
import java.util.List;

import static javax.xml.bind.DatatypeConverter.printHexBinary;

public class TcHelperUser {
    public String username;

    public byte[] salt;

    public byte[] userKeyKcv;

    public List<Credentials> credentials = new ArrayList<>();

    public static class Credentials {
        String serverId;
        String username;
        public byte[] passwordUnderUserKey;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("serverId", serverId)
                    .add("username", username)
                    .add("passwordUnderUserKey", printHexBinary(passwordUnderUserKey))
                    .toString();
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("username", username)
                .add("salt", printHexBinary(salt))
                .add("userKeyKcv", printHexBinary(userKeyKcv))
                .add("credentials", credentials)
                .toString();
    }
}
