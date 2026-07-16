package com.mytadika.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GradeCalculationServiceTest {

    private final GradeCalculationService service = new GradeCalculationService();

    @ParameterizedTest
    @CsvSource({
            "100, A", "80, A",
            "79.9, B", "70, B",
            "69.9, C", "60, C",
            "59.9, D", "50, D",
            "49.9, E", "40, E",
            "39.9, F", "0, F",
    })
    void calculateGrade_appliesMalaysiaPreschoolBoundaries(double averageMark, String expectedGrade) {
        assertThat(service.calculateGrade(averageMark)).isEqualTo(expectedGrade);
    }

    @Test
    void calculateAverage_returnsMeanOfScores() {
        assertThat(service.calculateAverage(List.of(80.0, 90.0, 70.0))).isEqualTo(80.0);
    }

    @Test
    void calculateAverage_returnsZeroForEmptyList() {
        assertThat(service.calculateAverage(List.of())).isEqualTo(0.0);
    }

    @Test
    void gradeLabel_mapsEachGradeToItsLabel() {
        assertThat(service.gradeLabel("A")).isEqualTo("Excellent");
        assertThat(service.gradeLabel("B")).isEqualTo("Good");
        assertThat(service.gradeLabel("C")).isEqualTo("Satisfactory");
        assertThat(service.gradeLabel("D")).isEqualTo("Passing");
        assertThat(service.gradeLabel("E")).isEqualTo("Borderline");
        assertThat(service.gradeLabel("F")).isEqualTo("Unsatisfactory");
    }
}
