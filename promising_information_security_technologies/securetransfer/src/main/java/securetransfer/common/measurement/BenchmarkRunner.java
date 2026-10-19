package securetransfer.common.measurement;

import java.util.Locale;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Прогревает один этап и измеряет серии его операций в текущем потоке.
 * Ввод-вывод и обработка результатов выполняются за пределами измеренного интервала.
 */
public final class BenchmarkRunner {
    /** Системный счётчик, созданный в том же потоке, где выполняются замеры. */
    private final CpuCycleCounter cycleCounter;

    /** Количество прогревочных и измеряемых операций. */
    private final BenchmarkSettings settings;

    /**
     * Наблюдаемый результат операций, затрудняющий удаление вычислений оптимизатором JVM.
     * Запись выполняется после второго чтения счётчика; это учебная защита, а не JMH Blackhole.
     */
    private static volatile Object consumedOperationResult;

    /**
     * Создаёт исполнитель, не принимая владение системным счётчиком.
     *
     * @param cycleCounter счётчик вызывающего потока
     * @param settings параметры прогрева и повторений
     */
    public BenchmarkRunner(CpuCycleCounter cycleCounter, BenchmarkSettings settings) {
        this.cycleCounter = Objects.requireNonNull(cycleCounter);
        this.settings = Objects.requireNonNull(settings);
    }

    /**
     * Измеряет пустую операцию тем же способом для оценки затрат самого измерения.
     * Результат показывается отдельно и не вычитается из криптографических операций.
     *
     * @return показания служебной серии
     */
    public StageMeasurement measureCounterOverhead() {
        return measureStage("Служебные затраты", "Пустая операция", operationIndex -> this);
    }

    /**
     * Выполняет прогрев, затем собирает исходные циклы повторных серий и выводит статистику.
     *
     * @param algorithmName название алгоритма
     * @param stageName название конкретного вычисления
     * @param operation операция без вывода, файлов и сети; получает изменяющийся индекс входа
     * @return неизменяемые показания, пригодные для последующей обработки
     */
    public StageMeasurement measureStage(
            String algorithmName,
            String stageName,
            IntFunction<?> operation
    ) {
        Objects.requireNonNull(operation);
        System.out.printf("%n%s — %s%n", algorithmName, stageName);
        System.out.printf("Прогрев: %d операций.%n", settings.warmupOperationCount());
        Object[] operationResults = new Object[settings.operationsPerMeasurement()];
        for (int completedOperations = 0; completedOperations < settings.warmupOperationCount();) {
            int batchOperationCount = Math.min(
                    settings.operationsPerMeasurement(), settings.warmupOperationCount() - completedOperations
            );
            measureBatch(operation, completedOperations, batchOperationCount, operationResults);
            completedOperations += batchOperationCount;
        }

        System.out.printf("Измерение: %d серий по %d операций.%n",
                settings.measurementCount(), settings.operationsPerMeasurement());
        long[] totalCyclesPerMeasurement = new long[settings.measurementCount()];
        for (int measurementIndex = 0; measurementIndex < settings.measurementCount(); measurementIndex++) {
            int firstOperationIndex = settings.warmupOperationCount()
                    + measurementIndex * settings.operationsPerMeasurement();
            totalCyclesPerMeasurement[measurementIndex] = measureBatch(
                    operation, firstOperationIndex, settings.operationsPerMeasurement(), operationResults
            );
        }

        StageMeasurement measurement = new StageMeasurement(
                algorithmName, stageName, settings.operationsPerMeasurement(), totalCyclesPerMeasurement
        );
        System.out.printf(Locale.ROOT,
                "Циклов/операцию: медиана %.1f; среднее %.1f; минимум %.1f; максимум %.1f.%n",
                measurement.calculateMedianCyclesPerOperation(),
                measurement.calculateMeanCyclesPerOperation(),
                measurement.calculateMinimumCyclesPerOperation(),
                measurement.calculateMaximumCyclesPerOperation());
        return measurement;
    }

    /**
     * Измеряет только цикл вызовов и сохранение их результатов в заранее выделенный массив.
     *
     * @param operation измеряемое действие
     * @param firstOperationIndex индекс первого входа серии
     * @param operationCount размер текущей серии
     * @param operationResults заранее выделенный буфер возвращаемых значений
     * @return циклы серии целиком
     * @throws IllegalStateException если счётчик не показал положительный интервал
     */
    private long measureBatch(
            IntFunction<?> operation,
            int firstOperationIndex,
            int operationCount,
            Object[] operationResults
    ) {
        long cyclesBeforeOperations = cycleCounter.readCurrentThreadCycles();
        for (int operationIndex = 0; operationIndex < operationCount; operationIndex++) {
            operationResults[operationIndex] = operation.apply(firstOperationIndex + operationIndex);
        }
        long cyclesAfterOperations = cycleCounter.readCurrentThreadCycles();

        // Используем каждый результат после измерения, сохраняя вычисления наблюдаемыми для JVM.
        for (int operationIndex = 0; operationIndex < operationCount; operationIndex++) {
            consumedOperationResult = operationResults[operationIndex];
        }
        long measuredCycles = cyclesAfterOperations - cyclesBeforeOperations;
        if (measuredCycles <= 0) {
            throw new IllegalStateException("Счётчик вернул неположительный интервал; замеры остановлены.");
        }
        return measuredCycles;
    }
}
