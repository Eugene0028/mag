package securetransfer.storage;

import securetransfer.core.GroupParameters;
import securetransfer.core.GroupValidator;
import securetransfer.core.ParticipantKeyPair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

/**
 * Сохранение и загрузка долговременной пары ключей участника.
 *
 * <p>Учебный текстовый формат хранит секретный ключ без шифрования.
 * Вместе с парой записываются p, q и g, чтобы проверить совместимость при загрузке.
 * На файловых системах POSIX новый файл доступен только владельцу.</p>
 */
public final class ParticipantKeyPairFileStorage {
    /**
     * Служебный конструктор класса статических файловых операций.
     */
    private ParticipantKeyPairFileStorage() {
    }

    /**
     * Проверяет пару и сохраняет её в новый файл UTF-8, создавая родительские каталоги.
     *
     * @param subgroupParameters предварительно проверенные параметры подгруппы
     * @param participantKeyPair сохраняемая пара секретного и открытого ключей
     * @param keyPairFilePath путь к новому файлу; существующий файл не перезаписывается
     * @throws IllegalArgumentException если пара ключей не прошла проверку
     * @throws IOException если файл уже существует либо каталог или файл недоступен
     */
    public static void saveKeyPair(
            GroupParameters subgroupParameters,
            ParticipantKeyPair participantKeyPair,
            Path keyPairFilePath
    ) throws IOException {
        GroupValidator.validateSubgroupKeyPair(subgroupParameters, participantKeyPair);

        Properties storedKeyPair = new Properties();
        storedKeyPair.setProperty("p", subgroupParameters.primeModulus().toString());
        storedKeyPair.setProperty("q", subgroupParameters.subgroupOrder().toString());
        storedKeyPair.setProperty("g", subgroupParameters.generator().toString());
        storedKeyPair.setProperty("privateKey", participantKeyPair.privateKey().toString());
        storedKeyPair.setProperty("publicKey", participantKeyPair.publicKey().toString());

        Path parentDirectory = keyPairFilePath.toAbsolutePath().getParent();
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        if (parentDirectory != null
                && Files.getFileStore(parentDirectory).supportsFileAttributeView("posix")) {
            Files.createFile(keyPairFilePath, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------")));
        } else {
            Files.createFile(keyPairFilePath);
        }

        try (BufferedWriter keyPairWriter = Files.newBufferedWriter(
                keyPairFilePath,
                StandardCharsets.UTF_8,
                StandardOpenOption.WRITE
        )) {
            storedKeyPair.store(keyPairWriter, "Long-term key pair and subgroup parameters");
        }

        System.out.println("Долговременная пара ключей сохранена: " + keyPairFilePath.toAbsolutePath());
    }

    /**
     * Читает пару, проверяет совпадение параметров подгруппы и корректность ключей.
     *
     * @param subgroupParameters предварительно проверенные параметры текущей подгруппы
     * @param keyPairFilePath путь к ранее сохранённой паре ключей
     * @return проверенная пара ключей участника
     * @throws IOException если файл недоступен, имеет неверный формат,
     *                     содержит другие параметры подгруппы или некорректные ключи
     */
    public static ParticipantKeyPair loadKeyPair(
            GroupParameters subgroupParameters,
            Path keyPairFilePath
    ) throws IOException {
        Properties storedKeyPair = new Properties();
        ParticipantKeyPair loadedKeyPair;

        try {
            try (BufferedReader keyPairReader = Files.newBufferedReader(
                    keyPairFilePath,
                    StandardCharsets.UTF_8
            )) {
                storedKeyPair.load(keyPairReader);
            }

            GroupParameters storedSubgroupParameters = new GroupParameters(
                    new BigInteger(storedKeyPair.getProperty("p", "")),
                    new BigInteger(storedKeyPair.getProperty("q", "")),
                    new BigInteger(storedKeyPair.getProperty("g", ""))
            );
            if (!subgroupParameters.equals(storedSubgroupParameters)) {
                throw new IOException("Пара ключей создана для других параметров подгруппы: " + keyPairFilePath);
            }

            loadedKeyPair = new ParticipantKeyPair(
                    new BigInteger(storedKeyPair.getProperty("privateKey", "")),
                    new BigInteger(storedKeyPair.getProperty("publicKey", ""))
            );
            GroupValidator.validateSubgroupKeyPair(subgroupParameters, loadedKeyPair);
        } catch (IllegalArgumentException invalidKeyPairException) {
            throw new IOException(
                    "Неверный формат или некорректная пара ключей в файле: " + keyPairFilePath,
                    invalidKeyPairException
            );
        }

        System.out.println("Долговременная пара ключей загружена: " + keyPairFilePath.toAbsolutePath());
        return loadedKeyPair;
    }
}
