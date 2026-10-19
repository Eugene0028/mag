package securetransfer.rgz.cipher;

import com.coderslagoon.crypto.estreamj.ciphers.sosemanuk.Sosemanuk;
import com.coderslagoon.crypto.estreamj.framework.ESJException;
import com.coderslagoon.crypto.estreamj.framework.ICipher;

import java.util.Arrays;

/**
 * Sosemanuk с ключом 128 бит через eSTREAMJ.
 *
 * <p>Библиотека обрабатывает полные блоки по 80 байт. Адаптер сохраняет остаток
 * потока, чтобы границы порций файла не меняли результат.</p>
 */
public final class SosemanukCipher implements KeyAndIvSetupCipher {
    /** Размер порции, которую eSTREAMJ обрабатывает без потери остатка. */
    private static final int STREAM_BLOCK_LENGTH_BYTES = 80;

    /** Нули для получения очередного блока ключевого потока через XOR. */
    private final byte[] zeroInputBlock = new byte[STREAM_BLOCK_LENGTH_BYTES];

    /** Остаток потока, ещё не использованный для обработки входа. */
    private final byte[] bufferedKeyStream = new byte[STREAM_BLOCK_LENGTH_BYTES];

    /** Позиция следующего байта остатка; размер буфера означает, что он исчерпан. */
    private int bufferedKeyStreamOffset = STREAM_BLOCK_LENGTH_BYTES;

    /** Библиотечное состояние с подготовленным ключом. */
    private Sosemanuk cipherEngine;

    /** Признак установки IV после последней подготовки ключа. */
    private boolean initialized;

    /** @return название алгоритма */
    @Override
    public String getName() {
        return "Sosemanuk";
    }

    /** @return длина ключа выбранной реализации: 16 байт */
    @Override
    public int getKeyLengthBytes() {
        return 16;
    }

    /** @return длина IV: 16 байт */
    @Override
    public int getInitializationVectorLengthBytes() {
        return 16;
    }

    /** {@inheritDoc} */
    @Override
    public void initialize(byte[] secretKey, byte[] initializationVector) {
        CipherArguments.validateKey(this, secretKey);
        CipherArguments.validateInitializationVector(this, initializationVector);
        setupKey(secretKey);
        setupInitializationVector(initializationVector);
    }

    /** {@inheritDoc} */
    @Override
    public void setupKey(byte[] secretKey) {
        CipherArguments.validateKey(this, secretKey);
        Sosemanuk preparedEngine = new Sosemanuk();
        try {
            preparedEngine.setupKey(ICipher.MODE_ENCRYPT, secretKey, 0);
        } catch (ESJException setupFailure) {
            throw new IllegalStateException("Не удалось подготовить ключ Sosemanuk", setupFailure);
        }
        cipherEngine = preparedEngine;
        initialized = false;
        bufferedKeyStreamOffset = STREAM_BLOCK_LENGTH_BYTES;
        Arrays.fill(bufferedKeyStream, (byte) 0);
    }

    /** {@inheritDoc} */
    @Override
    public void setupInitializationVector(byte[] initializationVector) {
        if (cipherEngine == null) {
            throw new IllegalStateException("Перед установкой IV Sosemanuk вызовите setupKey");
        }
        CipherArguments.validateInitializationVector(this, initializationVector);
        initialized = false;
        try {
            cipherEngine.setupNonce(initializationVector, 0);
        } catch (ESJException setupFailure) {
            throw new IllegalStateException("Не удалось установить IV Sosemanuk", setupFailure);
        }
        bufferedKeyStreamOffset = STREAM_BLOCK_LENGTH_BYTES;
        Arrays.fill(bufferedKeyStream, (byte) 0);
        initialized = true;
    }

    /** {@inheritDoc} */
    @Override
    public void processBytes(
            byte[] inputBytes, int inputOffset, int byteCount, byte[] outputBytes, int outputOffset
    ) {
        if (!initialized) {
            throw new IllegalStateException("Перед обработкой данных инициализируйте Sosemanuk ключом и IV");
        }
        CipherArguments.validateBuffers(inputBytes, inputOffset, byteCount, outputBytes, outputOffset);
        int processedByteCount = 0;
        while (processedByteCount < byteCount && bufferedKeyStreamOffset < STREAM_BLOCK_LENGTH_BYTES) {
            outputBytes[outputOffset + processedByteCount] = (byte) (inputBytes[inputOffset + processedByteCount]
                    ^ bufferedKeyStream[bufferedKeyStreamOffset++]);
            processedByteCount++;
        }

        int alignedByteCount = (byteCount - processedByteCount)
                / STREAM_BLOCK_LENGTH_BYTES * STREAM_BLOCK_LENGTH_BYTES;
        try {
            if (alignedByteCount > 0) {
                cipherEngine.process(inputBytes, inputOffset + processedByteCount,
                        outputBytes, outputOffset + processedByteCount, alignedByteCount);
                processedByteCount += alignedByteCount;
            }
            if (processedByteCount < byteCount) {
                cipherEngine.process(zeroInputBlock, 0, bufferedKeyStream, 0, STREAM_BLOCK_LENGTH_BYTES);
                bufferedKeyStreamOffset = 0;
                while (processedByteCount < byteCount) {
                    outputBytes[outputOffset + processedByteCount] = (byte) (inputBytes[inputOffset + processedByteCount]
                            ^ bufferedKeyStream[bufferedKeyStreamOffset++]);
                    processedByteCount++;
                }
            }
        } catch (ESJException processingFailure) {
            initialized = false;
            throw new IllegalStateException("Не удалось обработать данные Sosemanuk", processingFailure);
        }
    }
}
