package org.apache.ignite.ci.issue;

public class ChangeUi {
    public final String username;
    public final String webUrl;

    public ChangeUi(String username, String webUrl) {
        this.username = username;
        this.webUrl = webUrl;
    }

    public String toHtml() {
        return "<a href='" + webUrl + "'>" + username + "</a>";
    }
}
