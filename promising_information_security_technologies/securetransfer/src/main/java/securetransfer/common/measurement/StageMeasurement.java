package securetransfer.common.measurement;

import java.util.Arrays;
import java.util.Objects;

/**
 * Неизменяемые исходные показания одного этапа после прогрева.
 *
 * @param algorithmName название алгоритма
 * @param stageName название этапа
 * @param operationsPerMeasurement число операций внутри одного измеренного интервала
 * @param totalCyclesPerMeasurement циклы каждой серии целиком, включая служебные затраты
 */
public record StageMeasurement(
        String algorithmName,
        String stageName,
        int operationsPerMeasurement,
        long[] totalCyclesPerMeasurement
) {
    /** Проверяет показания и сохраняет собственную копию массива. */
    public StageMeasurement {
        Objects.requireNonNull(algorithmName);
        Objects.requireNonNull(stageName);
        Objects.requireNonNull(totalCyclesPerMeasurement);
        if (operationsPerMeasurement < 1 || totalCyclesPerMeasurement.length == 0) {
            throw new IllegalArgumentException("Нужны непустые показания и положительный размер серии.");
        }
        for (long measuredCycles : totalCyclesPerMeasurement) {
            if (measuredCycles <= 0) {
                throw new IllegalArgumentException("Измеренный интервал должен содержать положительное число циклов.");
            }
        }
        totalCyclesPerMeasurement = totalCyclesPerMeasurement.clone();
    }

    /**
     * Возвращает копию исходных циклов серий, чтобы вызывающий код не изменил результат.
     *
     * @return исходные показания без усреднения и вычитания накладных расходов
     */
    @Override
    public long[] totalCyclesPerMeasurement() {
        return totalCyclesPerMeasurement.clone();
    }

    /**
     * Нормирует минимальную стоимость серии на одну операцию.
     *
     * @return минимум циклов на операцию
     */
    public double calculateMinimumCyclesPerOperation() {
        return Arrays.stream(totalCyclesPerMeasurement).min().orElseThrow()
                / (double) operationsPerMeasurement;
    }

    /**
     * Нормирует максимальную стоимость серии на одну операцию.
     *
     * @return максимум циклов на операцию
     */
    public double calculateMaximumCyclesPerOperation() {
        return Arrays.stream(totalCyclesPerMeasurement).max().orElseThrow()
                / (double) operationsPerMeasurement;
    }

    /**
     * Вычисляет среднее число циклов на операцию по всем сериям.
     *
     * @return среднее арифметическое циклов на операцию
     */
    public double calculateMeanCyclesPerOperation() {
        return Arrays.stream(totalCyclesPerMeasurement).asDoubleStream().average().orElseThrow()
                / operationsPerMeasurement;
    }

    /**
     * Вычисляет медиану стоимости серий и делит её на количество операций в серии.
     *
     * @return медиана серий, выраженная в циклах на операцию
     */
    public double calculateMedianCyclesPerOperation() {
        long[] sortedCycleCounts = totalCyclesPerMeasurement.clone();
        Arrays.sort(sortedCycleCounts);
        int middleIndex = sortedCycleCounts.length / 2;
        double medianCycles = sortedCycleCounts.length % 2 == 0
                ? sortedCycleCounts[middleIndex - 1] / 2.0 + sortedCycleCounts[middleIndex] / 2.0
                : sortedCycleCounts[middleIndex];
        return medianCycles / operationsPerMeasurement;
    }
}
