package org.apache.ignite.ci.github;

import com.google.common.base.Objects;

public class GitHubUser {
    public String login;
    public String avatar_url;
    /*
     * "user": {
     *       "login": "glukos",
     *       "id": 2736390,
     *       "node_id": "MDQ6VXNlcjI3MzYzOTA=",
     *       "avatar_url": "https://avatars0.githubusercontent.com/u/2736390?v=4",
     *       "gravatar_id": "",
     *       "url": "https://api.github.com/users/glukos",
     *       "html_url": "https://github.com/glukos",
     *       "followers_url": "https://api.github.com/users/glukos/followers",
     *       "following_url": "https://api.github.com/users/glukos/following{/other_user}",
     *       "gists_url": "https://api.github.com/users/glukos/gists{/gist_id}",
     *       "starred_url": "https://api.github.com/users/glukos/starred{/owner}{/repo}",
     *       "subscriptions_url": "https://api.github.com/users/glukos/subscriptions",
     *       "organizations_url": "https://api.github.com/users/glukos/orgs",
     *       "repos_url": "https://api.github.com/users/glukos/repos",
     *       "events_url": "https://api.github.com/users/glukos/events{/privacy}",
     *       "received_events_url": "https://api.github.com/users/glukos/received_events",
     *       "type": "User",
     *       "site_admin": false
     *     },
     */

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GitHubUser user = (GitHubUser)o;
        return Objects.equal(login, user.login) &&
            Objects.equal(avatar_url, user.avatar_url);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(login, avatar_url);
    }
}
