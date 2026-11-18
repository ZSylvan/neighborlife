
package com.reviewhelix.probe;

import java.util.List;

public final class ReviewHelixProbe {

    private ReviewHelixProbe() {
    }

    public static String firstItem(List<String> items) {
        return items.get(0).trim();
    }
}
