package securetransfer.common.protocol;

import securetransfer.common.config.ApplicationDataPaths;
import securetransfer.common.config.GroupGenerationSettings;
import securetransfer.common.core.DiffieHellman;
import securetransfer.common.core.GroupParameters;
import securetransfer.common.core.GroupValidator;
import securetransfer.common.core.ParticipantKeyPair;
import securetransfer.common.core.SignedPublicKeyDirectory;
import securetransfer.common.storage.GroupParameterFileStorage;
import securetransfer.common.storage.ParticipantKeyPairFileStorage;
import securetransfer.common.storage.SignedPublicKeyDirectoryFileStorage;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Map;
import java.util.Objects;

/**
 * Подготовка параметров и ключей, создание выбранного протокола для сервера или клиента.
 *
 * <p>В учебном сценарии сервер представляет Алису, клиент — Боба.
 * MQV использует предварительно согласованные долговременные ключи и подписанный справочник.</p>
 */
public final class KeyAgreementProtocolFactory {
    /** Размеры групп, выбранные использующим фабрику приложением. */
    private final GroupGenerationSettings groupGenerationSettings;

    /** Собственные рабочие файлы использующего фабрику приложения. */
    private final ApplicationDataPaths dataPaths;

    /**
     * Создаёт фабрику с настройками конкретного приложения.
     *
     * @param groupGenerationSettings размеры p и q для обмена ключами
     * @param dataPaths пути параметров, долговременных пар и доверенных материалов
     */
    public KeyAgreementProtocolFactory(
            GroupGenerationSettings groupGenerationSettings,
            ApplicationDataPaths dataPaths
    ) {
        this.groupGenerationSettings = Objects.requireNonNull(groupGenerationSettings);
        this.dataPaths = Objects.requireNonNull(dataPaths);
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
    public KeyAgreementProtocol createServerProtocol(
            ProtocolType protocolType
    ) throws IOException, GeneralSecurityException {
        GroupParameters groupParameters;
        if (protocolType == ProtocolType.DH_FULL_GROUP) {
            groupParameters = GroupParameterFileStorage.loadOrGenerateFullGroupParameters(
                    dataPaths.fullGroupParametersFile(), groupGenerationSettings.modulusBitLength()
            );
        } else {
            groupParameters = GroupParameterFileStorage.loadOrGenerateSubgroupParameters(
                    dataPaths.subgroupParametersFile(),
                    groupGenerationSettings.modulusBitLength(),
                    groupGenerationSettings.subgroupOrderBitLength()
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
    public KeyAgreementProtocol createClientProtocol(
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
    private KeyAgreementProtocol createProtocol(
            ProtocolType protocolType,
            GroupParameters groupParameters,
            boolean serverParticipant
    ) throws IOException, GeneralSecurityException {
        validateConfiguredGroupSize(protocolType, groupParameters);
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
    private KeyAgreementProtocol createMqvProtocol(
            GroupParameters subgroupParameters,
            boolean serverParticipant
    ) throws IOException, GeneralSecurityException {
        GroupValidator.validateSubgroupParameters(subgroupParameters);
        Path ownLongTermKeyFile = serverParticipant
                ? dataPaths.aliceLongTermKeyFile() : dataPaths.bobLongTermKeyFile();
        String ownParticipantName = serverParticipant ? "Alice" : "Bob";
        String otherParticipantName = serverParticipant ? "Bob" : "Alice";

        if (!Files.isRegularFile(ownLongTermKeyFile) || !Files.isRegularFile(dataPaths.signedDirectoryFile())
                || !Files.isRegularFile(dataPaths.authorityPublicKeyFile())) {
            throw new IOException("Файлы MQV не подготовлены. Нужны " + ownLongTermKeyFile
                    + ", " + dataPaths.signedDirectoryFile() + " и " + dataPaths.authorityPublicKeyFile()
                    + ". Порядок подготовки описан в README.md.");
        }

        Map<String, BigInteger> verifiedPublicKeys = SignedPublicKeyDirectoryFileStorage.loadAndVerifyDirectory(
                subgroupParameters, dataPaths.signedDirectoryFile(), dataPaths.authorityPublicKeyFile()
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
    private void prepareMqvCredentials(
            GroupParameters subgroupParameters
    ) throws IOException, GeneralSecurityException {
        boolean directoryExists = Files.exists(dataPaths.signedDirectoryFile());
        boolean authorityPublicKeyExists = Files.exists(dataPaths.authorityPublicKeyFile());
        if (directoryExists || authorityPublicKeyExists) {
            if (!directoryExists || !authorityPublicKeyExists) {
                throw new IOException("Комплект файлов доверия MQV неполон: восстановите справочник и ключ центра.");
            }
            return;
        }

        ParticipantKeyPair aliceLongTermKeyPair = loadOrCreateLongTermKeyPair(
                subgroupParameters, dataPaths.aliceLongTermKeyFile()
        );
        ParticipantKeyPair bobLongTermKeyPair = loadOrCreateLongTermKeyPair(
                subgroupParameters, dataPaths.bobLongTermKeyFile()
        );
        KeyPair signingAuthorityKeyPair = SignedPublicKeyDirectory.generateSigningAuthorityKeyPair();
        SignedPublicKeyDirectory signedDirectory = SignedPublicKeyDirectory.createSignedDirectory(
                subgroupParameters,
                Map.of("Alice", aliceLongTermKeyPair.publicKey(), "Bob", bobLongTermKeyPair.publicKey()),
                signingAuthorityKeyPair.getPrivate()
        );
        SignedPublicKeyDirectoryFileStorage.saveAuthorityPublicKey(
                signingAuthorityKeyPair.getPublic(), dataPaths.authorityPublicKeyFile()
        );
        SignedPublicKeyDirectoryFileStorage.saveDirectory(signedDirectory, dataPaths.signedDirectoryFile());
        System.out.println("Файлы MQV подготовлены в каталоге " + dataPaths.rootDirectory());
    }

    /**
     * Проверяет соответствие загруженных или полученных по сети параметров настройкам приложения.
     *
     * @param protocolType тип полной группы или подгруппы
     * @param groupParameters параметры текущего обмена
     * @throws IllegalArgumentException если размеры p или q не соответствуют выбранным настройкам
     */
    private void validateConfiguredGroupSize(ProtocolType protocolType, GroupParameters groupParameters) {
        boolean modulusSizeMatches = groupParameters.primeModulus().bitLength()
                == groupGenerationSettings.modulusBitLength();
        boolean subgroupSizeMatches = protocolType == ProtocolType.DH_FULL_GROUP
                || groupParameters.subgroupOrder().bitLength() == groupGenerationSettings.subgroupOrderBitLength();
        if (!modulusSizeMatches || !subgroupSizeMatches) {
            throw new IllegalArgumentException("Размеры параметров группы не соответствуют настройкам приложения.");
        }
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
