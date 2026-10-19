package securetransfer.rgz.file;

import securetransfer.rgz.cipher.StreamCipherAlgorithm;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Метаданные одного файла, включаемые в проверку целостности вместе с шифротекстом.
 *
 * @param protocolVersion версия формата файла
 * @param fileName только имя файла без каталогов
 * @param fileSizeBytes размер исходного файла; шифротекст имеет такую же длину
 * @param cipherAlgorithm идентификатор потокового шифра
 * @param initializationVector IV, зарезервированный отправителем
 */
public record FileHeader(
        int protocolVersion,
        String fileName,
        long fileSizeBytes,
        StreamCipherAlgorithm cipherAlgorithm,
        byte[] initializationVector
) {
    /** Поддерживаемая версия передачи файла. */
    public static final int CURRENT_VERSION = 1;

    /** Максимальная длина имени в байтах UTF-8. */
    public static final int MAXIMUM_FILE_NAME_BYTES = 255;

    /** Проверяет версию, переносимое имя, размер и длину IV; сохраняет копию IV. */
    public FileHeader {
        Objects.requireNonNull(fileName, "Имя файла отсутствует");
        Objects.requireNonNull(cipherAlgorithm, "Шифр отсутствует");
        Objects.requireNonNull(initializationVector, "IV отсутствует");
        if (protocolVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Неподдерживаемая версия передачи файла: " + protocolVersion);
        }
        validateFileName(fileName);
        if (fileSizeBytes < 0) {
            throw new IllegalArgumentException("Размер файла не может быть отрицательным");
        }
        if (initializationVector.length != cipherAlgorithm.createCipher().getInitializationVectorLengthBytes()) {
            throw new IllegalArgumentException("Длина IV не соответствует шифру");
        }
        initializationVector = initializationVector.clone();
    }

    /** @return копия IV, защищающая заголовок от изменения снаружи */
    @Override
    public byte[] initializationVector() {
        return initializationVector.clone();
    }

    /**
     * Запрещает пути и имена, недопустимые в распространённых файловых системах.
     *
     * @param fileName проверяемое имя без каталогов
     */
    private static void validateFileName(String fileName) {
        if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")
                || fileName.endsWith(".") || fileName.endsWith(" ")
                || !StandardCharsets.UTF_8.newEncoder().canEncode(fileName)
                || fileName.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_FILE_NAME_BYTES
                || fileName.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")) {
            throw new IllegalArgumentException("Непереносимое или слишком длинное имя файла");
        }
        for (int characterIndex = 0; characterIndex < fileName.length(); characterIndex++) {
            char character = fileName.charAt(characterIndex);
            if (Character.isISOControl(character) || "/\\:*?\"<>|".indexOf(character) >= 0) {
                throw new IllegalArgumentException("Имя файла содержит запрещённый символ или путь");
            }
        }
    }
}
