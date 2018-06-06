package org.apache.ignite.ci.web.rest.login;


import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.issue.IssueDetector;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.CredentialsUi;
import org.apache.ignite.ci.web.model.PrincipalResponse;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.ci.web.model.TcHelperUserUi;
import org.apache.ignite.ci.web.model.UserMenuResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import static org.apache.ignite.ci.web.rest.login.Login.checkServiceUserAndPassword;


@Path(UserService.USER)
@Produces(MediaType.APPLICATION_JSON)
public class UserService {
    public static final String USER = "user";

    @Context
    private ServletContext context;

    @Context
    private HttpServletRequest request;

    @GET
    @Path("currentUserName")
    public SimpleResult currentUserName() {
        final ICredentialsProv prov = ICredentialsProv.get(request);
        if (prov == null)
            return new SimpleResult("");


        final ITcHelper helper = CtxListener.getTcHelper(context);

        return userMenu(prov, helper);
    }

    @NotNull public SimpleResult userMenu(ICredentialsProv prov, ITcHelper helper) {
        final UserAndSessionsStorage users = helper.users();
        final TcHelperUser user = users.getUser(prov.getPrincipalId());

        UserMenuResult res = new UserMenuResult(user.getDisplayName());

        res.authorizedState = helper.issueDetector().isAuthorized();

        return res;
    }

    @POST
    @Path("authorize")
    public SimpleResult setAuthorizedState() {
        final ICredentialsProv prov = ICredentialsProv.get(request);

        final ITcHelper helper = CtxListener.getTcHelper(context);

        IssueDetector detector = helper.issueDetector();

        detector.startBackgroundCheck(helper, prov);

        return userMenu(prov, helper);
    }

    @GET
    @Path("get")
    public TcHelperUserUi getUserData(@Nullable @QueryParam("login") final String loginParm) {
        final String login = Strings.isNullOrEmpty(loginParm) ? currentPrincipal().login : loginParm;
        final TcHelperUser user = CtxListener.getTcHelper(context).users().getUser(login);

        final TcHelperUserUi tcHelperUserUi = new TcHelperUserUi(user);

        final ICredentialsProv prov = ICredentialsProv.get(request);

        //if principal is not null, can do only brief check of credentials.

        for (TcHelperUser.Credentials next : user.getCredentialsList()) {
            final CredentialsUi credentialsUi = new CredentialsUi();
            credentialsUi.serviceId = next.getServerId();
            credentialsUi.serviceLogin = next.getUsername();

            final byte[] encPass = next.getPasswordUnderUserKey();
            credentialsUi.servicePassword = encPass != null && encPass.length > 0 ? "*******" : "";

            tcHelperUserUi.data.add(credentialsUi);
        }

        //todo if user is not current disable add creds
        return tcHelperUserUi;
    }


    PrincipalResponse currentPrincipal() {
        return new PrincipalResponse(ICredentialsProv.get(request).getPrincipalId());
    }


    @POST
    @Path("resetCredentials")
    public SimpleResult resetCredentials() {
        final ICredentialsProv prov = ICredentialsProv.get(request);
        final String currentUserLogin = prov.getPrincipalId();
        final UserAndSessionsStorage users = CtxListener.getTcHelper(context).users();
        final TcHelperUser user = users.getUser(currentUserLogin);

        user.userKeyKcv = null;
        user.getCredentialsList().clear();
        user.salt = null;

        users.putUser(currentUserLogin, user);

        return new SimpleResult("");
    }


    @POST
    @Path("addService")
    public SimpleResult addCredentials(@FormParam("serviceId") String serviceId,
                                       @FormParam("serviceLogin") String serviceLogin,
                                       @FormParam("servicePassword") String servicePassword) {
        Preconditions.checkState(!Strings.isNullOrEmpty(serviceId));
        Preconditions.checkState(!Strings.isNullOrEmpty(serviceLogin));
        Preconditions.checkState(!Strings.isNullOrEmpty(servicePassword));

        final ICredentialsProv prov = ICredentialsProv.get(request);
        final String currentUserLogin = prov.getPrincipalId();
        final UserAndSessionsStorage users = CtxListener.getTcHelper(context).users();
        final TcHelperUser user = users.getUser(currentUserLogin);

        //todo check service credentials first

        final User tcAddUser = checkServiceUserAndPassword(serviceId, serviceLogin, servicePassword);

        if (tcAddUser == null) {
            return new SimpleResult("Service rejected credentials/user not found");
        }

        final TcHelperUser.Credentials credentials = new TcHelperUser.Credentials( serviceId, serviceLogin
        );

        credentials.setPassword(servicePassword, prov.getUserKey());

        user.enrichUserData(tcAddUser);

        user.getCredentialsList().add(credentials);

        users.putUser(currentUserLogin, user);

        return new SimpleResult("");
    }

}
