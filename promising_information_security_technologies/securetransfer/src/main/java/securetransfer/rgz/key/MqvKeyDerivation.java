package securetransfer.rgz.key;

import securetransfer.common.core.GroupParameters;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Получение ключа потокового шифра из уже вычисленного общего секрета MQV.
 *
 * <p>Схема РГЗ: число → беззнаковые байты фиксированной длины → SHA3-256
 * → первые требуемые байты хеша. Параметры группы и открытые ключи должны быть
 * проверены до вычисления общего секрета. Объект используется в одном потоке.</p>
 */
public final class MqvKeyDerivation {
    /** Модуль p, определяющий диапазон общего секрета. */
    private final BigInteger primeModulus;

    /** Единая длина представления секрета: ceil(bitLength(p) / 8). */
    private final int encodedSecretLengthBytes;

    /** Объект SHA3-256 для последовательных вызовов. */
    private final Sha3Hasher hashFunction;

    /**
     * Задаёт формат секрета по общим параметрам подгруппы.
     *
     * @param subgroupParameters предварительно проверенные параметры MQV
     * @param hashFunction хеш-функция, принадлежащая вызывающему потоку
     * @throws NullPointerException если параметры, p или хеш-функция равны {@code null}
     * @throws IllegalArgumentException если p слишком мал для допустимого секрета
     */
    public MqvKeyDerivation(GroupParameters subgroupParameters, Sha3Hasher hashFunction) {
        Objects.requireNonNull(subgroupParameters, "Параметры подгруппы не должны быть null");
        primeModulus = Objects.requireNonNull(subgroupParameters.primeModulus(), "Модуль p не должен быть null");
        if (primeModulus.compareTo(BigInteger.valueOf(3)) <= 0) {
            throw new IllegalArgumentException("Модуль p должен быть больше 3");
        }
        encodedSecretLengthBytes = (primeModulus.bitLength() - 1) / Byte.SIZE + 1;
        this.hashFunction = Objects.requireNonNull(hashFunction, "Хеш-функция не должна быть null");
    }

    /**
     * Записывает секрет старшим байтом вперёд, без знакового байта, дополняя слева нулями.
     *
     * <p>BigInteger.toByteArray возвращает знаковое представление: у положительного
     * числа может появиться начальный нулевой байт. Здесь он удаляется перед
     * дополнением до фиксированной длины. Десятичные строки и hex не используются.</p>
     *
     * @param sharedSecret общий секрет, возвращённый MQV
     * @return новый массив фиксированной длины для хеширования
     * @throws NullPointerException если секрет равен {@code null}
     * @throws IllegalArgumentException если секрет не находится в диапазоне 2…p−2
     */
    public byte[] encodeSharedSecret(BigInteger sharedSecret) {
        Objects.requireNonNull(sharedSecret, "Общий секрет не должен быть null");
        if (sharedSecret.compareTo(BigInteger.ONE) <= 0
                || sharedSecret.compareTo(primeModulus.subtract(BigInteger.ONE)) >= 0) {
            throw new IllegalArgumentException("Общий секрет MQV должен находиться в диапазоне 2…p−2");
        }

        byte[] signedSecretBytes = sharedSecret.toByteArray();
        int significantBytesOffset = signedSecretBytes[0] == 0 ? 1 : 0;
        int significantByteCount = signedSecretBytes.length - significantBytesOffset;
        byte[] encodedSecret = new byte[encodedSecretLengthBytes];
        System.arraycopy(signedSecretBytes, significantBytesOffset, encodedSecret,
                encodedSecret.length - significantByteCount, significantByteCount);
        Arrays.fill(signedSecretBytes, (byte) 0);
        return encodedSecret;
    }

    /**
     * Кодирует секрет, вычисляет SHA3-256 и выделяет нужное количество байтов ключа.
     *
     * <p>Текущим четырём шифрам нужны первые 16 байт хеша. Запрос более 32 байт
     * запрещён: расширение результата нулями не выполняется. Временные массивы
     * секрета и полного хеша очищаются после копирования ключа.</p>
     *
     * @param sharedSecret общий секрет MQV
     * @param keyLengthBytes длина ключа из getKeyLengthBytes выбранного шифра
     * @return новый массив ключа указанной длины
     * @throws IllegalArgumentException если длина вне диапазона 1…32 или секрет недопустим
     * @throws NullPointerException если секрет равен {@code null}
     */
    public byte[] deriveKey(BigInteger sharedSecret, int keyLengthBytes) {
        if (keyLengthBytes < 1 || keyLengthBytes > Sha3Hasher.HASH_LENGTH_BYTES) {
            throw new IllegalArgumentException("Длина ключа должна находиться в диапазоне 1…32 байта");
        }
        byte[] encodedSecret = encodeSharedSecret(sharedSecret);
        byte[] fullHash = null;
        try {
            fullHash = hashFunction.calculateHash(encodedSecret);
            return Arrays.copyOf(fullHash, keyLengthBytes);
        } finally {
            Arrays.fill(encodedSecret, (byte) 0);
            if (fullHash != null) {
                Arrays.fill(fullHash, (byte) 0);
            }
        }
    }

    /** @return единая длина представления секрета в байтах */
    public int getEncodedSecretLengthBytes() {
        return encodedSecretLengthBytes;
    }

    /**
     * Получает отдельный 32-байтовый ключ HMAC, связанный с открытыми данными сеанса.
     *
     * <p>Вход SHA3-256: метка назначения, фиксированные байты секрета и 32-байтовый
     * хеш открытого обмена. Этот вход отличается от входа получения ключа шифрования.</p>
     *
     * @param sharedSecret общий секрет MQV
     * @param exchangeTranscriptHash SHA3-256 открытых сообщений в порядке сервер, клиент
     * @return отдельный ключ аутентификации
     * @throws IllegalArgumentException если хеш обмена имеет неверную длину
     */
    public byte[] deriveAuthenticationKey(BigInteger sharedSecret, byte[] exchangeTranscriptHash) {
        Objects.requireNonNull(exchangeTranscriptHash, "Хеш обмена отсутствует");
        if (exchangeTranscriptHash.length != Sha3Hasher.HASH_LENGTH_BYTES) {
            throw new IllegalArgumentException("Хеш открытого обмена должен содержать 32 байта");
        }
        byte[] purposeLabel = "securetransfer/rgz/file-auth/v1\0".getBytes(StandardCharsets.UTF_8);
        byte[] encodedSecret = encodeSharedSecret(sharedSecret);
        byte[] authenticationInput = new byte[purposeLabel.length + encodedSecret.length + exchangeTranscriptHash.length];
        try {
            System.arraycopy(purposeLabel, 0, authenticationInput, 0, purposeLabel.length);
            System.arraycopy(encodedSecret, 0, authenticationInput, purposeLabel.length, encodedSecret.length);
            System.arraycopy(exchangeTranscriptHash, 0, authenticationInput,
                    purposeLabel.length + encodedSecret.length, exchangeTranscriptHash.length);
            return hashFunction.calculateHash(authenticationInput);
        } finally {
            Arrays.fill(encodedSecret, (byte) 0);
            Arrays.fill(authenticationInput, (byte) 0);
        }
    }
}
