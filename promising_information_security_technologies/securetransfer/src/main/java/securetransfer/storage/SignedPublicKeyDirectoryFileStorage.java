package securetransfer.storage;

import securetransfer.core.GroupParameters;
import securetransfer.core.SignedPublicKeyDirectory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.Properties;

/**
 * Хранение подписанного справочника и отдельного доверенного открытого ключа центра.
 *
 * <p>При проверке доверенный ключ читается из локального файла, подготовленного заранее.
 * Сетевой собеседник не задаёт этот ключ.</p>
 */
public final class SignedPublicKeyDirectoryFileStorage {
    /**
     * Служебный конструктор класса файловых операций.
     */
    private SignedPublicKeyDirectoryFileStorage() {
    }

    /**
     * Сохраняет подписанный справочник в новый файл UTF-8.
     *
     * @param signedDirectory справочник, подписанный центром
     * @param directoryFilePath путь к новому файлу
     * @throws IOException если файл существует или запись не удалась
     */
    public static void saveDirectory(
            SignedPublicKeyDirectory signedDirectory,
            Path directoryFilePath
    ) throws IOException {
        createParentDirectory(directoryFilePath);
        try (BufferedWriter directoryWriter = Files.newBufferedWriter(
                directoryFilePath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        )) {
            signedDirectory.toProperties().store(directoryWriter, "Signed MQV public key directory");
        }
        System.out.println("Подписанный справочник сохранён: " + directoryFilePath.toAbsolutePath());
    }

    /**
     * Сохраняет открытый ключ центра в отдельный новый файл в формате X.509.
     *
     * @param authorityPublicKey открытый ключ центра Ed25519
     * @param authorityPublicKeyFilePath путь к новому доверенному файлу
     * @throws IOException если файл существует или запись не удалась
     */
    public static void saveAuthorityPublicKey(
            PublicKey authorityPublicKey,
            Path authorityPublicKeyFilePath
    ) throws IOException {
        createParentDirectory(authorityPublicKeyFilePath);
        Files.write(authorityPublicKeyFilePath, authorityPublicKey.getEncoded(),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        System.out.println("Доверенный открытый ключ центра сохранён: " + authorityPublicKeyFilePath.toAbsolutePath());
    }

    /**
     * Загружает справочник и возвращает его записи только после проверки подписи и параметров.
     *
     * @param subgroupParameters проверенные параметры текущей подгруппы
     * @param directoryFilePath путь к подписанному справочнику
     * @param authorityPublicKeyFilePath путь к заранее доверенному открытому ключу центра
     * @return проверенные долговременные открытые ключи по именам участников
     * @throws IOException если файлы недоступны или поля справочника некорректны
     * @throws GeneralSecurityException если ключ центра или подпись неверны
     */
    public static Map<String, BigInteger> loadAndVerifyDirectory(
            GroupParameters subgroupParameters,
            Path directoryFilePath,
            Path authorityPublicKeyFilePath
    ) throws IOException, GeneralSecurityException {
        byte[] encodedAuthorityPublicKey = Files.readAllBytes(authorityPublicKeyFilePath);
        PublicKey trustedAuthorityPublicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(encodedAuthorityPublicKey));

        try {
            Properties directoryProperties = new Properties();
            try (BufferedReader directoryReader = Files.newBufferedReader(directoryFilePath, StandardCharsets.UTF_8)) {
                directoryProperties.load(directoryReader);
            }
            SignedPublicKeyDirectory signedDirectory = SignedPublicKeyDirectory.fromProperties(directoryProperties);
            return signedDirectory.verifySignatureAndGetPublicKeys(subgroupParameters, trustedAuthorityPublicKey);
        } catch (IllegalArgumentException invalidDirectoryException) {
            throw new IOException("Некорректный справочник MQV: " + directoryFilePath, invalidDirectoryException);
        }
    }

    /**
     * Создаёт родительский каталог сохраняемого файла, если он отсутствует.
     *
     * @param filePath путь к сохраняемому файлу
     * @throws IOException если каталог недоступен
     */
    private static void createParentDirectory(Path filePath) throws IOException {
        Path parentDirectory = filePath.toAbsolutePath().getParent();
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }
    }
}
