package securetransfer.rgz.file;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Контрольная сумма для сверки экспериментальных файлов вне таймеров протокола. */
public final class FileChecksum {
    /** Запрещает создание экземпляров набора функций. */
    private FileChecksum() {
    }

    /**
     * Читает файл порциями и вычисляет SHA-256 без загрузки всего файла в память.
     * @param file обычный файл
     * @return SHA-256 в шестнадцатеричном виде
     * @throws IOException при ошибке чтения
     */
    public static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException unavailableAlgorithm) {
            throw new IllegalStateException("В JDK отсутствует SHA-256", unavailableAlgorithm);
        }
        try (DigestInputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
