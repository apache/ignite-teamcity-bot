package org.apache.ignite.ci.tcbot.jira.v3.adf;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all elements that include sub-elements.
 */
public abstract class ElementContainer extends Element {
    private List<Element> content;

    protected ElementContainer(String type) {
        super(type);
    }

    protected void appendInternal(Element element) {
        if (content == null)
            content = new ArrayList<>();

        content.add(element);
    }

    public boolean isEmpty() {
        return content == null || content.isEmpty();
    }
}
