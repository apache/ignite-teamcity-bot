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

    public String toSlackMarkup() {
        String str = username
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
        return "<" + webUrl + "|" + str + ">";
    }

    public String toPlainText() {
        return username + " " + webUrl;
    }
}
