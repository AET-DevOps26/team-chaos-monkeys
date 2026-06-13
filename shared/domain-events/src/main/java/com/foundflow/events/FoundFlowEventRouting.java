package com.foundflow.events;

public final class FoundFlowEventRouting {

    public static final String EXCHANGE = "foundflow.domain-events";
    public static final String DEAD_LETTER_EXCHANGE = "foundflow.dlx.v1";

    public static final String LOST_REPORT_CREATED = "lost-report.created.v1";
    public static final String LOST_REPORT_UPDATED = "lost-report.updated.v1";
    public static final String FOUND_ITEM_LOGGED = "found-item.logged.v1";
    public static final String FOUND_ITEM_UPDATED = "found-item.updated.v1";
    public static final String MATCH_CANDIDATE_CREATED = "match-candidate.created.v1";

    public static final String MATCHING_LOST_REPORTS_QUEUE = "matching.lost-report-created.v1";
    public static final String MATCHING_LOST_REPORT_UPDATES_QUEUE = "matching.lost-report-updated.v1";
    public static final String MATCHING_FOUND_ITEMS_QUEUE = "matching.found-item-logged.v1";
    public static final String MATCHING_FOUND_ITEM_UPDATES_QUEUE = "matching.found-item-updated.v1";
    public static final String MATCHING_MATCH_CANDIDATES_QUEUE = "matching.match-candidate-created.v1";

    private FoundFlowEventRouting() {
    }

    public static String deadLetterQueue(String queueName) {
        return queueName + ".dlq";
    }

    public static String deadLetterRoutingKey(String queueName) {
        return queueName + ".dlq";
    }
}
