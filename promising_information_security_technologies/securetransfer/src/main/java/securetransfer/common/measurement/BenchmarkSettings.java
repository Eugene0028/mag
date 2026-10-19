package securetransfer.common.measurement;

/**
 * Количество повторений для измерения произвольных вычислительных этапов.
 *
 * @param warmupOperationCount количество операций прогрева каждого этапа
 * @param measurementCount количество сохраняемых замеров каждого этапа
 * @param operationsPerMeasurement количество операций между двумя чтениями счётчика
 */
public record BenchmarkSettings(
        int warmupOperationCount,
        int measurementCount,
        int operationsPerMeasurement
) {
    /**
     * Проверяет число повторений и отсутствие переполнения индекса операций.
     */
    public BenchmarkSettings {
        if (warmupOperationCount < 1 || measurementCount < 1 || operationsPerMeasurement < 1) {
            throw new IllegalArgumentException("Прогрев, число замеров и размер серии должны быть положительными.");
        }
        try {
            Math.addExact(warmupOperationCount, Math.multiplyExact(measurementCount, operationsPerMeasurement));
        } catch (ArithmeticException tooManyOperations) {
            throw new IllegalArgumentException("Суммарное число операций превышает диапазон int.", tooManyOperations);
        }
    }
}
