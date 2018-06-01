package org.apache.ignite.ci.user;

import com.google.common.base.MoreObjects;
import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.changes.ChangesListRef;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.ProblemOccurrencesRef;
import org.apache.ignite.ci.tcmodel.result.StatisticsRef;
import org.apache.ignite.ci.tcmodel.result.TestOccurrencesRef;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.util.ArrayList;
import java.util.List;

import static javax.xml.bind.DatatypeConverter.printHexBinary;

public class TcHelperUser implements IVersionedEntity {
    public static final int LATEST_VERSION = 2;
    @SuppressWarnings("FieldCanBeLocal")
    public Integer _version = LATEST_VERSION;

    public String username;

    public byte[] salt;

    public byte[] userKeyKcv;

    public List<Credentials> credentials = new ArrayList<>();

    @Override
    public int version() {
        return _version == null ? 0 : _version;
    }

    @Override
    public int latestVersion() {
        return LATEST_VERSION;
    }

    public static class Credentials {
        String serverId;
        String username;
        byte[] passwordUnderUserKey;

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
