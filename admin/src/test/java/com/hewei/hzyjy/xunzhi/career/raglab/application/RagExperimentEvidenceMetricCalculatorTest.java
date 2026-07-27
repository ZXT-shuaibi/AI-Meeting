package com.hewei.hzyjy.xunzhi.career.raglab.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RagExperimentEvidenceMetricCalculatorTest {

    @Test
    void calculatesAccuracyOnlyFromManuallyAnnotatedEvidence() {
        RagExperimentEvidenceMetricCalculator calculator = new RagExperimentEvidenceMetricCalculator();

        assertEquals(2D / 3D, calculator.accuracy(List.of(true, false, true)));
        assertNull(calculator.accuracy(List.of()));
    }
}
