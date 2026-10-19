package securetransfer.protocol;

import securetransfer.core.DiffieHellman;
import securetransfer.core.GroupParameters;
import securetransfer.core.GroupValidator;
import securetransfer.core.ParticipantKeyPair;
import securetransfer.core.SignedPublicKeyDirectory;
import securetransfer.storage.GroupParameterFileStorage;
import securetransfer.storage.ParticipantKeyPairFileStorage;
import securetransfer.storage.SignedPublicKeyDirectoryFileStorage;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Map;

/**
 * Подготовка параметров и ключей, создание выбранного протокола для сервера или клиента.
 *
 * <p>В учебном сценарии сервер представляет Алису, клиент — Боба.
 * MQV использует предварительно согласованные долговременные ключи и подписанный справочник.</p>
 */
public final class KeyAgreementProtocolFactory {
    /**
     * Долговременная пара Алисы, используемая сервером MQV.
     */
    private static final Path ALICE_LONG_TERM_KEY_FILE = Path.of("data", "keys", "alice-long-term.properties");

    /**
     * Долговременная пара Боба, используемая клиентом MQV.
     */
    private static final Path BOB_LONG_TERM_KEY_FILE = Path.of("data", "keys", "bob-long-term.properties");

    /**
     * Общий подписанный справочник долговременных открытых ключей.
     */
    private static final Path SIGNED_DIRECTORY_FILE = Path.of("data", "trust", "mqv-directory.properties");

    /**
     * Заранее доверенный открытый ключ центра, хранящийся отдельно от справочника.
     */
    private static final Path AUTHORITY_PUBLIC_KEY_FILE = Path.of("data", "trust", "authority-public.der");

    /**
     * Служебный конструктор фабрики статических методов.
     */
    private KeyAgreementProtocolFactory() {
    }

    /**
     * Подготавливает параметры и создаёт выбранный протокол серверной стороны.
     *
     * <p>При первом использовании MQV выполняет однократную подготовку файлов двух участников.
     * Для отдельных компьютеров файлы клиента передаются ему заранее вне протокола обмена.</p>
     *
     * @param protocolType алгоритм, выбранный в меню сервера
     * @return серверный участник выбранного алгоритма
     * @throws IOException если параметры, ключи или справочник недоступны
     * @throws GeneralSecurityException если подпись справочника или ключ центра неверны
     */
    public static KeyAgreementProtocol createServerProtocol(
            ProtocolType protocolType
    ) throws IOException, GeneralSecurityException {
        // Сохраняем маленькие размеры чисел для разбора учебных вычислений.
        int modulusBitLength = 16;
        int subgroupOrderBitLength = 8;
        GroupParameters groupParameters;
        if (protocolType == ProtocolType.DH_FULL_GROUP) {
            groupParameters = GroupParameterFileStorage.loadOrGenerateFullGroupParameters(
                    Path.of("data", "params", "full-group.properties"), modulusBitLength
            );
        } else {
            groupParameters = GroupParameterFileStorage.loadOrGenerateSubgroupParameters(
                    Path.of("data", "params", "subgroup.properties"), modulusBitLength, subgroupOrderBitLength
            );
        }

        if (protocolType == ProtocolType.MQV) {
            prepareMqvCredentials(groupParameters);
        }
        return createProtocol(protocolType, groupParameters, true);
    }

    /**
     * Проверяет параметры сервера и создаёт клиентский экземпляр выбранного алгоритма.
     *
     * @param protocolType алгоритм, указанный сервером в сетевом сообщении
     * @param receivedGroupParameters открытые параметры, полученные от сервера
     * @return клиентский участник выбранного алгоритма
     * @throws IOException если собственные ключи или доверенные файлы MQV отсутствуют либо неверны
     * @throws GeneralSecurityException если подпись справочника или ключ центра неверны
     * @throws IllegalArgumentException если полученные параметры некорректны
     */
    public static KeyAgreementProtocol createClientProtocol(
            ProtocolType protocolType,
            GroupParameters receivedGroupParameters
    ) throws IOException, GeneralSecurityException {
        return createProtocol(protocolType, receivedGroupParameters, false);
    }

    /**
     * Выбирает реализацию и создаёт собственные ключи текущего обмена.
     *
     * @param protocolType выбранный алгоритм
     * @param groupParameters параметры выбранной группы
     * @param serverParticipant true для серверной стороны, false для клиентской
     * @return реализация общего контракта обмена
     * @throws IOException если файлы MQV недоступны
     * @throws GeneralSecurityException если проверка доверия MQV не удалась
     * @throws IllegalArgumentException если параметры группы неверны
     */
    private static KeyAgreementProtocol createProtocol(
            ProtocolType protocolType,
            GroupParameters groupParameters,
            boolean serverParticipant
    ) throws IOException, GeneralSecurityException {
        return switch (protocolType) {
            case DH_FULL_GROUP -> {
                GroupValidator.validateFullGroupParameters(groupParameters);
                ParticipantKeyPair ownKeyPair = DiffieHellman.generateKeyPair(groupParameters);
                yield new FullGroupDhProtocol(groupParameters, ownKeyPair);
            }
            case DH_SUBGROUP -> {
                GroupValidator.validateSubgroupParameters(groupParameters);
                ParticipantKeyPair ownKeyPair = DiffieHellman.generateSubgroupKeyPair(groupParameters);
                yield new SubgroupDhProtocol(groupParameters, ownKeyPair);
            }
            case MQV -> createMqvProtocol(groupParameters, serverParticipant);
        };
    }

    /**
     * Загружает собственный долговременный ключ и проверенный справочник, создаёт временную пару MQV.
     *
     * @param subgroupParameters параметры подгруппы текущего обмена
     * @param serverParticipant true для Алисы на сервере, false для Боба на клиенте
     * @return участник MQV с подтверждённым ключом ожидаемого собеседника
     * @throws IOException если файлы отсутствуют или собственный ключ не совпадает со справочником
     * @throws GeneralSecurityException если ключ центра или подпись справочника неверны
     * @throws IllegalArgumentException если параметры подгруппы некорректны
     */
    private static KeyAgreementProtocol createMqvProtocol(
            GroupParameters subgroupParameters,
            boolean serverParticipant
    ) throws IOException, GeneralSecurityException {
        GroupValidator.validateSubgroupParameters(subgroupParameters);
        Path ownLongTermKeyFile = serverParticipant ? ALICE_LONG_TERM_KEY_FILE : BOB_LONG_TERM_KEY_FILE;
        String ownParticipantName = serverParticipant ? "Alice" : "Bob";
        String otherParticipantName = serverParticipant ? "Bob" : "Alice";

        if (!Files.isRegularFile(ownLongTermKeyFile) || !Files.isRegularFile(SIGNED_DIRECTORY_FILE)
                || !Files.isRegularFile(AUTHORITY_PUBLIC_KEY_FILE)) {
            throw new IOException("Файлы MQV не подготовлены. Нужны " + ownLongTermKeyFile
                    + ", " + SIGNED_DIRECTORY_FILE + " и " + AUTHORITY_PUBLIC_KEY_FILE
                    + ". Порядок подготовки описан в README.md.");
        }

        Map<String, BigInteger> verifiedPublicKeys = SignedPublicKeyDirectoryFileStorage.loadAndVerifyDirectory(
                subgroupParameters, SIGNED_DIRECTORY_FILE, AUTHORITY_PUBLIC_KEY_FILE
        );
        ParticipantKeyPair ownLongTermKeyPair = ParticipantKeyPairFileStorage.loadKeyPair(
                subgroupParameters, ownLongTermKeyFile
        );
        if (!ownLongTermKeyPair.publicKey().equals(verifiedPublicKeys.get(ownParticipantName))) {
            throw new IOException("Собственный долговременный ключ не совпадает с записью " + ownParticipantName);
        }
        BigInteger otherParticipantPublicKey = verifiedPublicKeys.get(otherParticipantName);
        if (otherParticipantPublicKey == null) {
            throw new IOException("В подписанном справочнике отсутствует участник " + otherParticipantName);
        }

        ParticipantKeyPair ownEphemeralKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
        return new MqvProtocol(subgroupParameters, ownLongTermKeyPair, ownEphemeralKeyPair, otherParticipantPublicKey);
    }

    /**
     * При отсутствии файлов доверия создаёт долговременные пары и подписывает общий справочник.
     *
     * <p>Существующие файлы доверия не заменяются. Секретный ключ учебного центра
     * используется для однократной подписи и не сохраняется.</p>
     *
     * @param subgroupParameters проверенные параметры подгруппы
     * @throws IOException если файлы недоступны или комплект файлов доверия неполон
     * @throws GeneralSecurityException если подпись справочника не удалось создать
     */
    private static void prepareMqvCredentials(
            GroupParameters subgroupParameters
    ) throws IOException, GeneralSecurityException {
        boolean directoryExists = Files.exists(SIGNED_DIRECTORY_FILE);
        boolean authorityPublicKeyExists = Files.exists(AUTHORITY_PUBLIC_KEY_FILE);
        if (directoryExists || authorityPublicKeyExists) {
            if (!directoryExists || !authorityPublicKeyExists) {
                throw new IOException("Комплект файлов доверия MQV неполон: восстановите справочник и ключ центра.");
            }
            return;
        }

        ParticipantKeyPair aliceLongTermKeyPair = loadOrCreateLongTermKeyPair(
                subgroupParameters, ALICE_LONG_TERM_KEY_FILE
        );
        ParticipantKeyPair bobLongTermKeyPair = loadOrCreateLongTermKeyPair(
                subgroupParameters, BOB_LONG_TERM_KEY_FILE
        );
        KeyPair signingAuthorityKeyPair = SignedPublicKeyDirectory.generateSigningAuthorityKeyPair();
        SignedPublicKeyDirectory signedDirectory = SignedPublicKeyDirectory.createSignedDirectory(
                subgroupParameters,
                Map.of("Alice", aliceLongTermKeyPair.publicKey(), "Bob", bobLongTermKeyPair.publicKey()),
                signingAuthorityKeyPair.getPrivate()
        );
        SignedPublicKeyDirectoryFileStorage.saveAuthorityPublicKey(
                signingAuthorityKeyPair.getPublic(), AUTHORITY_PUBLIC_KEY_FILE
        );
        SignedPublicKeyDirectoryFileStorage.saveDirectory(signedDirectory, SIGNED_DIRECTORY_FILE);
        System.out.println("Файлы MQV подготовлены. На одном компьютере сервер и клиент используют общую папку проекта.");
    }

    /**
     * Загружает существующую долговременную пару или создаёт её для первичной подготовки MQV.
     *
     * @param subgroupParameters проверенные параметры подгруппы
     * @param keyPairFilePath путь к долговременной паре участника
     * @return существующая или новая проверенная пара
     * @throws IOException если пару не удалось прочитать или сохранить
     */
    private static ParticipantKeyPair loadOrCreateLongTermKeyPair(
            GroupParameters subgroupParameters,
            Path keyPairFilePath
    ) throws IOException {
        if (Files.exists(keyPairFilePath)) {
            return ParticipantKeyPairFileStorage.loadKeyPair(subgroupParameters, keyPairFilePath);
        }
        ParticipantKeyPair generatedKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
        ParticipantKeyPairFileStorage.saveKeyPair(subgroupParameters, generatedKeyPair, keyPairFilePath);
        return generatedKeyPair;
    }
}
