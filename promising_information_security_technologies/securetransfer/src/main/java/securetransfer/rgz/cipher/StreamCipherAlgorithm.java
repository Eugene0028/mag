package securetransfer.rgz.cipher;

import java.util.function.Supplier;

/** Четыре алгоритма РГЗ и создание нового независимого состояния каждого из них. */
public enum StreamCipherAlgorithm {
    /** HC-128 из Bouncy Castle. */
    HC_128(1, Hc128Cipher::new),

    /** Rabbit по RFC 4503. */
    RABBIT(2, RabbitCipher::new),

    /** Salsa20 с 20 раундами из Bouncy Castle. */
    SALSA20(3, Salsa20Cipher::new),

    /** Sosemanuk из eSTREAMJ. */
    SOSEMANUK(4, SosemanukCipher::new);

    /** Постоянный идентификатор в формате передачи; не зависит от ordinal. */
    private final int protocolId;

    /** Способ создания конкретной реализации без заранее подготовленного ключа. */
    private final Supplier<StreamCipher> cipherFactory;

    /**
     * Сохраняет способ создания реализации.
     *
     * @param protocolId постоянный идентификатор алгоритма в протоколе
     * @param cipherFactory конструктор конкретного шифра
     */
    StreamCipherAlgorithm(int protocolId, Supplier<StreamCipher> cipherFactory) {
        this.protocolId = protocolId;
        this.cipherFactory = cipherFactory;
    }

    /**
     * Создаёт отдельный объект для нового потока данных.
     *
     * @return шифр, который необходимо инициализировать ключом и IV
     */
    public StreamCipher createCipher() {
        return cipherFactory.get();
    }

    /** @return постоянный идентификатор алгоритма в сетевом заголовке */
    public int getProtocolId() {
        return protocolId;
    }

    /**
     * Находит алгоритм по сетевому идентификатору.
     *
     * @param protocolId идентификатор из заголовка
     * @return известный алгоритм
     * @throws IllegalArgumentException если идентификатор неизвестен
     */
    public static StreamCipherAlgorithm fromProtocolId(int protocolId) {
        for (StreamCipherAlgorithm algorithm : values()) {
            if (algorithm.protocolId == protocolId) {
                return algorithm;
            }
        }
        throw new IllegalArgumentException("Неизвестный идентификатор шифра: " + protocolId);
    }
}
