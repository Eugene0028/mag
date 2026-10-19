package securetransfer.rgz.cipher;

/**
 * Rabbit с ключом 128 бит и IV 64 бита по разделу 2 RFC 4503.
 *
 * <p>Ключ, IV и выходные слова представлены младшим байтом вперёд
 * (little endian). Числа в приложениях RFC записаны старшим байтом вперёд;
 * при сопоставлении требуется учитывать это представление.</p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4503.html">Описание Rabbit авторами</a>
 */
public final class RabbitCipher implements KeyAndIvSetupCipher {
    /** Приращения восьми счётчиков по разделу 2.5 RFC 4503. */
    private static final int[] COUNTER_INCREMENTS = {
            0x4D34D34D, 0xD34D34D3, 0x34D34D34, 0x4D34D34D,
            0xD34D34D3, 0x34D34D34, 0x4D34D34D, 0xD34D34D3
    };

    /** Восемь текущих слов состояния X. */
    private final int[] stateWords = new int[8];

    /** Восемь текущих счётчиков C. */
    private final int[] counterWords = new int[8];

    /** Промежуточные значения нелинейной функции g. */
    private final int[] nonlinearValues = new int[8];

    /** Состояние X после подготовки ключа, до применения IV. */
    private final int[] keyStateWords = new int[8];

    /** Счётчики C после подготовки ключа, до применения IV. */
    private final int[] keyCounterWords = new int[8];

    /** Блок из 16 байт ключевого потока с сохранением неиспользованного остатка. */
    private final byte[] keyStreamBlock = new byte[16];

    /** Позиция следующего байта блока; 16 означает, что нужен новый блок. */
    private int keyStreamOffset = 16;

    /** Текущий перенос между счётчиками и раундами. */
    private int counterCarry;

    /** Сохранённый перенос после подготовки ключа. */
    private int keyCounterCarry;

    /** Признак наличия состояния, подготовленного по ключу. */
    private boolean keyPrepared;

    /** Признак установки IV и готовности к обработке байтов. */
    private boolean initialized;

    /** @return название алгоритма */
    @Override
    public String getName() {
        return "Rabbit";
    }

    /** @return длина ключа: 16 байт */
    @Override
    public int getKeyLengthBytes() {
        return 16;
    }

    /** @return длина IV: 8 байт */
    @Override
    public int getInitializationVectorLengthBytes() {
        return 8;
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
        int[] keyParts = new int[8];
        for (int partIndex = 0; partIndex < keyParts.length; partIndex++) {
            keyParts[partIndex] = (secretKey[partIndex * 2] & 0xFF)
                    | ((secretKey[partIndex * 2 + 1] & 0xFF) << 8);
        }
        for (int wordIndex = 0; wordIndex < stateWords.length; wordIndex++) {
            if ((wordIndex & 1) == 0) {
                stateWords[wordIndex] = (keyParts[(wordIndex + 1) & 7] << 16) | keyParts[wordIndex];
                counterWords[wordIndex] = (keyParts[(wordIndex + 4) & 7] << 16)
                        | keyParts[(wordIndex + 5) & 7];
            } else {
                stateWords[wordIndex] = (keyParts[(wordIndex + 5) & 7] << 16)
                        | keyParts[(wordIndex + 4) & 7];
                counterWords[wordIndex] = (keyParts[wordIndex] << 16) | keyParts[(wordIndex + 1) & 7];
            }
        }
        counterCarry = 0;
        for (int roundIndex = 0; roundIndex < 4; roundIndex++) {
            advanceState();
        }
        for (int wordIndex = 0; wordIndex < counterWords.length; wordIndex++) {
            counterWords[wordIndex] ^= stateWords[(wordIndex + 4) & 7];
        }
        System.arraycopy(stateWords, 0, keyStateWords, 0, stateWords.length);
        System.arraycopy(counterWords, 0, keyCounterWords, 0, counterWords.length);
        keyCounterCarry = counterCarry;
        keyPrepared = true;
        initialized = false;
        keyStreamOffset = keyStreamBlock.length;
    }

    /** {@inheritDoc} */
    @Override
    public void setupInitializationVector(byte[] initializationVector) {
        if (!keyPrepared) {
            throw new IllegalStateException("Перед установкой IV Rabbit вызовите setupKey");
        }
        CipherArguments.validateInitializationVector(this, initializationVector);
        System.arraycopy(keyStateWords, 0, stateWords, 0, stateWords.length);
        System.arraycopy(keyCounterWords, 0, counterWords, 0, counterWords.length);
        counterCarry = keyCounterCarry;

        int lowerIvWord = readLittleEndianInt(initializationVector, 0);
        int upperIvWord = readLittleEndianInt(initializationVector, 4);
        int mixedUpperIvWord = (upperIvWord & 0xFFFF0000) | (lowerIvWord >>> 16);
        int mixedLowerIvWord = (upperIvWord << 16) | (lowerIvWord & 0xFFFF);
        for (int wordIndex = 0; wordIndex < counterWords.length; wordIndex += 4) {
            counterWords[wordIndex] ^= lowerIvWord;
            counterWords[wordIndex + 1] ^= mixedUpperIvWord;
            counterWords[wordIndex + 2] ^= upperIvWord;
            counterWords[wordIndex + 3] ^= mixedLowerIvWord;
        }
        for (int roundIndex = 0; roundIndex < 4; roundIndex++) {
            advanceState();
        }
        keyStreamOffset = keyStreamBlock.length;
        initialized = true;
    }

    /** {@inheritDoc} */
    @Override
    public void processBytes(
            byte[] inputBytes, int inputOffset, int byteCount, byte[] outputBytes, int outputOffset
    ) {
        if (!initialized) {
            throw new IllegalStateException("Перед обработкой данных инициализируйте Rabbit ключом и IV");
        }
        CipherArguments.validateBuffers(inputBytes, inputOffset, byteCount, outputBytes, outputOffset);
        for (int byteIndex = 0; byteIndex < byteCount; byteIndex++) {
            if (keyStreamOffset == keyStreamBlock.length) {
                generateKeyStreamBlock();
            }
            outputBytes[outputOffset + byteIndex] = (byte) (inputBytes[inputOffset + byteIndex]
                    ^ keyStreamBlock[keyStreamOffset++]);
        }
    }

    /** Обновляет счётчики и состояние X на один раунд по разделам 2.5–2.6 RFC. */
    private void advanceState() {
        for (int wordIndex = 0; wordIndex < counterWords.length; wordIndex++) {
            long counterSum = Integer.toUnsignedLong(counterWords[wordIndex])
                    + Integer.toUnsignedLong(COUNTER_INCREMENTS[wordIndex]) + counterCarry;
            counterWords[wordIndex] = (int) counterSum;
            counterCarry = (int) (counterSum >>> 32);

            long stateAndCounter = Integer.toUnsignedLong(stateWords[wordIndex] + counterWords[wordIndex]);
            long squaredValue = stateAndCounter * stateAndCounter;
            nonlinearValues[wordIndex] = (int) (squaredValue ^ (squaredValue >>> 32));
        }
        for (int wordIndex = 0; wordIndex < stateWords.length; wordIndex++) {
            int previousValue = nonlinearValues[(wordIndex + 7) & 7];
            int earlierValue = nonlinearValues[(wordIndex + 6) & 7];
            if ((wordIndex & 1) == 0) {
                stateWords[wordIndex] = nonlinearValues[wordIndex]
                        + Integer.rotateLeft(previousValue, 16) + Integer.rotateLeft(earlierValue, 16);
            } else {
                stateWords[wordIndex] = nonlinearValues[wordIndex]
                        + Integer.rotateLeft(previousValue, 8) + earlierValue;
            }
        }
    }

    /** Выполняет раунд и извлекает следующие 128 бит ключевого потока. */
    private void generateKeyStreamBlock() {
        advanceState();
        for (int outputWordIndex = 0; outputWordIndex < 4; outputWordIndex++) {
            int stateIndex = outputWordIndex * 2;
            int outputWord = stateWords[stateIndex]
                    ^ (stateWords[(stateIndex + 5) & 7] >>> 16)
                    ^ (stateWords[(stateIndex + 3) & 7] << 16);
            writeLittleEndianInt(outputWord, keyStreamBlock, outputWordIndex * Integer.BYTES);
        }
        keyStreamOffset = 0;
    }

    /**
     * Читает 32 бита младшим байтом вперёд.
     *
     * @param bytes исходный массив
     * @param offset смещение первого байта
     * @return битовое представление 32-битного слова
     */
    private static int readLittleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16) | ((bytes[offset + 3] & 0xFF) << 24);
    }

    /**
     * Записывает 32 бита младшим байтом вперёд.
     *
     * @param value записываемое слово
     * @param bytes массив результата
     * @param offset смещение первого байта
     */
    private static void writeLittleEndianInt(int value, byte[] bytes, int offset) {
        for (int byteIndex = 0; byteIndex < Integer.BYTES; byteIndex++) {
            bytes[offset + byteIndex] = (byte) (value >>> (byteIndex * Byte.SIZE));
        }
    }
}
