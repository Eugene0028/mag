package securetransfer.rgz.measurement;

import securetransfer.common.measurement.BenchmarkRunner;
import securetransfer.common.measurement.StageMeasurement;
import securetransfer.rgz.cipher.KeyAndIvSetupCipher;
import securetransfer.rgz.cipher.StreamCipher;
import securetransfer.rgz.cipher.StreamCipherAlgorithm;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Подготовка входов вне замеров и сравнение вычислительных этапов потоковых шифров. */
public final class StreamCipherBenchmark {
    /** Название сопоставимой для всех алгоритмов полной подготовки. */
    public static final String INITIALIZATION_STAGE = "Инициализация: объект, ключ и IV";

    /** Название генерации порции потока без повторной установки ключа или IV. */
    public static final String KEY_STREAM_STAGE = "Генерация ключевого потока";

    /** Исполнитель прогрева и замеров в текущем потоке. */
    private final BenchmarkRunner measurementRunner;

    /** Число байтов в одной измеряемой порции потока. */
    private final int keyStreamBlockLengthBytes;

    /** Количество входов, покрывающее все прогревочные и измеряемые операции. */
    private final int preparedInputCount;

    /**
     * Сохраняет настройки подготовки и общий исполнитель измерений.
     *
     * @param measurementRunner исполнитель, использующий системный счётчик
     * @param keyStreamBlockLengthBytes размер одной порции потока
     * @param preparedInputCount общее количество прогревочных и измеряемых операций этапа
     */
    public StreamCipherBenchmark(
            BenchmarkRunner measurementRunner, int keyStreamBlockLengthBytes, int preparedInputCount
    ) {
        this.measurementRunner = Objects.requireNonNull(measurementRunner);
        if (keyStreamBlockLengthBytes < 1 || preparedInputCount < 1) {
            throw new IllegalArgumentException("Размер порции и число подготовленных входов должны быть положительными");
        }
        this.keyStreamBlockLengthBytes = keyStreamBlockLengthBytes;
        this.preparedInputCount = preparedInputCount;
    }

    /**
     * Измеряет полную подготовку, доступные отдельные этапы и генерацию потока.
     *
     * @param algorithm алгоритм, для которого создаются независимые состояния
     * @return результаты двух или четырёх этапов
     */
    public List<StageMeasurement> measureAlgorithm(StreamCipherAlgorithm algorithm) {
        StreamCipher cipherDescription = algorithm.createCipher();
        String algorithmName = cipherDescription.getName();
        System.out.println("\nПодготовка входов вне замеров: " + algorithmName);
        SecureRandom random = new SecureRandom();
        byte[][] preparedKeys = prepareRandomInputs(random, cipherDescription.getKeyLengthBytes());
        byte[][] preparedVectors = prepareRandomInputs(random, cipherDescription.getInitializationVectorLengthBytes());

        List<StageMeasurement> measurements = new ArrayList<>();
        measurements.add(measurementRunner.measureStage(algorithmName, INITIALIZATION_STAGE, operationIndex -> {
            StreamCipher initializedCipher = algorithm.createCipher();
            initializedCipher.initialize(preparedKeys[operationIndex], preparedVectors[operationIndex]);
            return initializedCipher;
        }));

        if (algorithm.createCipher() instanceof KeyAndIvSetupCipher separateSetupCipher) {
            measurements.add(measurementRunner.measureStage(algorithmName, "Подготовка ключа", operationIndex -> {
                separateSetupCipher.setupKey(preparedKeys[operationIndex]);
                return separateSetupCipher;
            }));
            separateSetupCipher.setupKey(preparedKeys[0]);
            measurements.add(measurementRunner.measureStage(algorithmName, "Установка IV", operationIndex -> {
                separateSetupCipher.setupInitializationVector(preparedVectors[operationIndex]);
                return separateSetupCipher;
            }));
        } else {
            System.out.println("Ключ и IV подготавливаются библиотекой совместно; раздельные замеры недоступны.");
        }

        StreamCipher streamingCipher = algorithm.createCipher();
        streamingCipher.initialize(preparedKeys[0], preparedVectors[0]);
        byte[] zeroInputBytes = new byte[keyStreamBlockLengthBytes];
        byte[] outputBytes = new byte[keyStreamBlockLengthBytes];
        measurements.add(measurementRunner.measureStage(algorithmName, KEY_STREAM_STAGE, operationIndex -> {
            streamingCipher.processBytes(zeroInputBytes, 0, zeroInputBytes.length, outputBytes, 0);
            return outputBytes;
        }));
        return List.copyOf(measurements);
    }

    /**
     * Генерирует все массивы до запуска измеряемых операций.
     *
     * @param random источник случайных байтов
     * @param inputLengthBytes длина одного ключа или IV
     * @return отдельный массив для каждой операции
     */
    private byte[][] prepareRandomInputs(SecureRandom random, int inputLengthBytes) {
        byte[][] inputs = new byte[preparedInputCount][inputLengthBytes];
        for (byte[] input : inputs) {
            random.nextBytes(input);
        }
        return inputs;
    }
}
