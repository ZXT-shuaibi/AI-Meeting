package com.hewei.hzyjy.xunzhi.career.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionIndexTest {

    @Test
    void recordsRecentDecisionsAndFormatsContext() {
        DecisionIndex index = new DecisionIndex();

        index.record("m1", 0, "JD aligned with Java backend");
        index.record("m1", 1, "Reflect decision NEXT");
        index.record("m1", 2, "Reflect decision PROBE");

        List<DecisionEntry> recent = index.getRecentDecisions("m1", 2);

        assertEquals(2, recent.size());
        assertEquals("Reflect decision NEXT", recent.get(0).summary());
        assertEquals("Reflect decision PROBE", recent.get(1).summary());
        assertTrue(index.formatDecisionContext("m1", 2).contains("Historical Key Decisions"));
    }

    @Test
    void clearsDecisionHistoryByMemoryId() {
        DecisionIndex index = new DecisionIndex();
        index.record("m1", 0, "keep");

        index.clear("m1");

        assertTrue(index.getDecisions("m1").isEmpty());
        assertEquals("", index.formatDecisionContext("m1", 5));
    }
}
