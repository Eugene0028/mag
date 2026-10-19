package securetransfer.rgz.cipher;

import java.util.Objects;

/** Проверки ключей, IV и участков массивов, общие для четырёх шифров РГЗ. */
final class CipherArguments {
    /** Запрещает создание экземпляров класса проверок. */
    private CipherArguments() {
    }

    /**
     * Проверяет наличие и длину ключа конкретного шифра.
     *
     * @param cipher шифр с заданной длиной ключа
     * @param secretKey проверяемый ключ
     */
    static void validateKey(StreamCipher cipher, byte[] secretKey) {
        Objects.requireNonNull(secretKey, "Секретный ключ не должен быть null");
        if (secretKey.length != cipher.getKeyLengthBytes()) {
            throw new IllegalArgumentException("Ключ " + cipher.getName() + " должен содержать "
                    + cipher.getKeyLengthBytes() + " байтов");
        }
    }

    /**
     * Проверяет наличие и длину IV конкретного шифра.
     *
     * @param cipher шифр с заданной длиной IV
     * @param initializationVector проверяемый IV
     */
    static void validateInitializationVector(StreamCipher cipher, byte[] initializationVector) {
        Objects.requireNonNull(initializationVector, "Вектор инициализации не должен быть null");
        if (initializationVector.length != cipher.getInitializationVectorLengthBytes()) {
            throw new IllegalArgumentException("IV " + cipher.getName() + " должен содержать "
                    + cipher.getInitializationVectorLengthBytes() + " байтов");
        }
    }

    /**
     * Проверяет границы массивов и запрещает их частичное перекрытие.
     *
     * @param inputBytes исходный массив
     * @param inputOffset начало исходного участка
     * @param byteCount длина участка
     * @param outputBytes массив результата
     * @param outputOffset начало участка результата
     */
    static void validateBuffers(
            byte[] inputBytes, int inputOffset, int byteCount, byte[] outputBytes, int outputOffset
    ) {
        Objects.requireNonNull(inputBytes, "Входной массив не должен быть null");
        Objects.requireNonNull(outputBytes, "Выходной массив не должен быть null");
        Objects.checkFromIndexSize(inputOffset, byteCount, inputBytes.length);
        Objects.checkFromIndexSize(outputOffset, byteCount, outputBytes.length);
        if (inputBytes == outputBytes
                && inputOffset != outputOffset
                && inputOffset < outputOffset + byteCount
                && outputOffset < inputOffset + byteCount) {
            throw new IllegalArgumentException("Участки одного массива пересекаются при разных смещениях");
        }
    }
}
