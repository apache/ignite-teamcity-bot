package org.apache.ignite.ci.tcbot;

import org.apache.ignite.ci.user.ICredentialsProv;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
class TcBotBgAuthImpl implements ITcBotBgAuth {
    /** Server authorizer credentials. */
    private ICredentialsProv srvAuthorizerCreds;

    /** {@inheritDoc} */
    @Override public void setServerAuthorizerCreds(ICredentialsProv creds) {
        this.srvAuthorizerCreds = creds;
    }

    /** {@inheritDoc} */
    @Nullable @Override public ICredentialsProv getServerAuthorizerCreds() {
        return srvAuthorizerCreds;
    }
}
