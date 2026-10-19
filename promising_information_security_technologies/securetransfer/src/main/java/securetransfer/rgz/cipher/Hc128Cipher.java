package securetransfer.rgz.cipher;

import org.bouncycastle.crypto.engines.HC128Engine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/**
 * Потоковый шифр HC-128 через реализацию Bouncy Castle.
 *
 * <p>Адаптер проверяет аргументы и предоставляет общий интерфейс проекта.
 * Подготовку состояния и обработку байтов выполняет {@link HC128Engine}.
 * Ключ и IV имеют длину по 16 байт.</p>
 */
public final class Hc128Cipher implements StreamCipher {
    /** Требуемая длина секретного ключа HC-128 в байтах. */
    private static final int KEY_LENGTH_BYTES = 16;

    /** Требуемая длина вектора инициализации HC-128 в байтах. */
    private static final int INITIALIZATION_VECTOR_LENGTH_BYTES = 16;

    /** Текущее состояние шифра; отсутствует до успешной инициализации. */
    private HC128Engine cipherEngine;

    /**
     * Возвращает название алгоритма.
     *
     * @return {@code HC-128}
     */
    @Override
    public String getName() {
        return "HC-128";
    }

    /**
     * Возвращает длину секретного ключа.
     *
     * @return 16 байт, то есть 128 бит
     */
    @Override
    public int getKeyLengthBytes() {
        return KEY_LENGTH_BYTES;
    }

    /**
     * Возвращает длину вектора инициализации.
     *
     * @return 16 байт, то есть 128 бит
     */
    @Override
    public int getInitializationVectorLengthBytes() {
        return INITIALIZATION_VECTOR_LENGTH_BYTES;
    }

    /**
     * Создаёт новое состояние HC-128 и полностью подготавливает его по ключу и IV.
     *
     * <p>Параметры Bouncy Castle копируют переданные массивы. Изменение аргументов
     * после вызова не влияет на состояние шифра. При повторной инициализации
     * прежняя позиция в потоке заменяется началом нового потока.</p>
     *
     * @param secretKey секретный ключ из 16 байтов
     * @param initializationVector вектор инициализации из 16 байтов
     * @throws NullPointerException если ключ или IV равен {@code null}
     * @throws IllegalArgumentException если ключ или IV имеет неверную длину
     */
    @Override
    public void initialize(byte[] secretKey, byte[] initializationVector) {
        CipherArguments.validateKey(this, secretKey);
        CipherArguments.validateInitializationVector(this, initializationVector);

        KeyParameter keyParameters = new KeyParameter(secretKey);
        ParametersWithIV cipherParameters = new ParametersWithIV(keyParameters, initializationVector);
        HC128Engine initializedEngine = new HC128Engine();

        // HC-128 выполняет шифрование и расшифрование одинаково; API требует этот флаг.
        initializedEngine.init(true, cipherParameters);
        cipherEngine = initializedEngine;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Проверки выполняются до обработки байтов. Позицию в потоке и остаток
     * уже сгенерированных байтов между вызовами сохраняет Bouncy Castle.</p>
     */
    @Override
    public void processBytes(
            byte[] inputBytes,
            int inputOffset,
            int byteCount,
            byte[] outputBytes,
            int outputOffset
    ) {
        if (cipherEngine == null) {
            throw new IllegalStateException("Перед обработкой данных вызовите initialize для HC-128");
        }

        CipherArguments.validateBuffers(inputBytes, inputOffset, byteCount, outputBytes, outputOffset);
        cipherEngine.processBytes(inputBytes, inputOffset, byteCount, outputBytes, outputOffset);
    }
}
