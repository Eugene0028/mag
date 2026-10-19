package securetransfer.measurement;

/**
 * Размеры чисел и количество повторений учебного измерения.
 *
 * @param modulusBitLength битовая длина p для всех трёх алгоритмов
 * @param subgroupOrderBitLength битовая длина q подгруппы DH и MQV
 * @param warmupOperationCount количество операций прогрева каждого этапа
 * @param measurementCount количество сохраняемых замеров каждого этапа
 * @param operationsPerMeasurement количество операций между двумя чтениями счётчика
 */
public record BenchmarkSettings(
        int modulusBitLength,
        int subgroupOrderBitLength,
        int warmupOperationCount,
        int measurementCount,
        int operationsPerMeasurement
) {
    /** Битовая длина p для всех трёх алгоритмов. */
    private static final int DEFAULT_MODULUS_BIT_LENGTH = 1024;

    /** Битовая длина q подгруппы DH и MQV. */
    private static final int DEFAULT_SUBGROUP_ORDER_BIT_LENGTH = 160;

    /** Количество операций прогрева каждого этапа. */
    private static final int DEFAULT_WARMUP_OPERATION_COUNT = 1000;

    /** Количество сохраняемых замеров каждого этапа. */
    private static final int DEFAULT_MEASUREMENT_COUNT = 100;

    /** Количество операций между двумя чтениями счётчика. */
    private static final int DEFAULT_OPERATIONS_PER_MEASUREMENT = 10;

    /**
     * Проверяет размеры чисел, число повторений и отсутствие переполнения индекса операций.
     */
    public BenchmarkSettings {
        if (modulusBitLength < 4 || subgroupOrderBitLength < 2
                || subgroupOrderBitLength >= modulusBitLength) {
            throw new IllegalArgumentException("Требуются p >= 4 бит и 2 <= длина q < длины p.");
        }
        if (warmupOperationCount < 1 || measurementCount < 1 || operationsPerMeasurement < 1) {
            throw new IllegalArgumentException("Прогрев, число замеров и размер серии должны быть положительными.");
        }
        try {
            Math.addExact(warmupOperationCount, Math.multiplyExact(measurementCount, operationsPerMeasurement));
        } catch (ArithmeticException tooManyOperations) {
            throw new IllegalArgumentException("Суммарное число операций превышает диапазон int.", tooManyOperations);
        }
    }

    /**
     * Создаёт стандартные настройки из именованных констант этого класса.
     *
     * @return настройки размеров чисел, прогрева и повторных замеров
     */
    public static BenchmarkSettings createDefault() {
        return new BenchmarkSettings(
                DEFAULT_MODULUS_BIT_LENGTH,
                DEFAULT_SUBGROUP_ORDER_BIT_LENGTH,
                DEFAULT_WARMUP_OPERATION_COUNT,
                DEFAULT_MEASUREMENT_COUNT,
                DEFAULT_OPERATIONS_PER_MEASUREMENT
        );
    }
}
