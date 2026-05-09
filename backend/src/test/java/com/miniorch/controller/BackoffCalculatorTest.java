package com.miniorch.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackoffCalculatorTest {

    private final BackoffCalculator calculator = new BackoffCalculator();

    @ParameterizedTest(name = "attempt {0} -> {1}s")
    @CsvSource({
            "0, 1",
            "1, 2",
            "2, 4",
            "3, 8",
            "4, 16",
            "5, 30",
            "10, 30",
            "100, 30"
    })
    @DisplayName("nextDelay doubles up to a 30s cap")
    void nextDelay_doublesAndCaps(int attempt, long expectedSeconds) {
        assertThat(calculator.nextDelay(attempt)).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }

    @Test
    @DisplayName("nextDelay rejects negative attempts")
    void nextDelay_rejectsNegative() {
        assertThatThrownBy(() -> calculator.nextDelay(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }
}
