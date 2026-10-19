package securetransfer.rgz.key;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * SHA3-256 через стандартный MessageDigest Java 17.
 *
 * <p>Один объект используется последовательно в одном потоке выполнения.
 * После digest библиотека сбрасывает состояние перед следующим сообщением.</p>
 */
public final class Sha3Hasher {
    /** Стандартное имя алгоритма в Java Cryptography Architecture. */
    public static final String ALGORITHM_NAME = "SHA3-256";

    /** Длина результата SHA3-256: 256 бит, то есть 32 байта. */
    public static final int HASH_LENGTH_BYTES = 32;

    /** Повторно используемый объект хеширования выбранного провайдера JDK. */
    private final MessageDigest messageDigest;

    /**
     * Подключает SHA3-256 без фиксации конкретного провайдера.
     *
     * @throws IllegalStateException если установленная Java не предоставляет SHA3-256
     */
    public Sha3Hasher() {
        try {
            messageDigest = MessageDigest.getInstance(ALGORITHM_NAME);
        } catch (NoSuchAlgorithmException unavailableAlgorithm) {
            throw new IllegalStateException("Установленная Java не предоставляет SHA3-256", unavailableAlgorithm);
        }
    }

    /**
     * Хеширует одно сообщение и возвращает новый массив результата.
     *
     * @param inputBytes исходное сообщение; массив не изменяется
     * @return 32 байта SHA3-256
     * @throws NullPointerException если сообщение равно {@code null}
     */
    public byte[] calculateHash(byte[] inputBytes) {
        Objects.requireNonNull(inputBytes, "Сообщение для хеширования не должно быть null");
        return messageDigest.digest(inputBytes);
    }

    /**
     * Описывает провайдер, реально выбранный установленной Java.
     *
     * @return имя и версия провайдера для отчёта
     */
    public String getProviderDescription() {
        return messageDigest.getProvider().getName() + " " + messageDigest.getProvider().getVersionStr();
    }
}
