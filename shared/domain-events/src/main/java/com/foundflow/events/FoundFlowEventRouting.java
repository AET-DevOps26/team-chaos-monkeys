package com.foundflow.events;

public final class FoundFlowEventRouting {

    public static final String EXCHANGE = "foundflow.domain-events";

    public static final String LOST_REPORT_CREATED = "lost-report.created.v1";
    public static final String FOUND_ITEM_LOGGED = "found-item.logged.v1";

    public static final String MATCHING_LOST_REPORTS_QUEUE = "matching.lost-report-created.v1";
    public static final String MATCHING_FOUND_ITEMS_QUEUE = "matching.found-item-logged.v1";

    private FoundFlowEventRouting() {
    }
}
