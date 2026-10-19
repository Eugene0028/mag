package securetransfer.core;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Подписанный центром справочник долговременных открытых ключей участников MQV.
 *
 * <p>Подпись охватывает имена, открытые ключи и параметры подгруппы.
 * Доверенный открытый ключ центра передаётся отдельно от справочника.
 * Справочник можно представить набором полей для сохранения в файл.</p>
 */
public final class SignedPublicKeyDirectory {
    /**
     * Параметры подгруппы, для которой центр подтвердил ключи участников.
     */
    private final GroupParameters subgroupParameters;

    /**
     * Неизменяемое соответствие между именами участников и их открытыми ключами.
     */
    private final Map<String, BigInteger> publicKeysByParticipant;

    /**
     * Байты подписи центра над всем содержимым справочника.
     */
    private final byte[] directorySignature;

    /**
     * Создаёт справочник с копиями записей и подписи.
     *
     * @param subgroupParameters параметры подгруппы, включённые в подпись
     * @param publicKeysByParticipant имена участников и их открытые ключи
     * @param directorySignature подпись центра
     */
    private SignedPublicKeyDirectory(
            GroupParameters subgroupParameters,
            Map<String, BigInteger> publicKeysByParticipant,
            byte[] directorySignature
    ) {
        this.subgroupParameters = subgroupParameters;
        this.publicKeysByParticipant = Map.copyOf(publicKeysByParticipant);
        this.directorySignature = directorySignature.clone();
    }

    /**
     * Создаёт отдельную пару ключей Ed25519 для подписи и проверки справочника.
     *
     * <p>Секретный ключ принадлежит центру. Его открытый ключ заранее известен участникам.</p>
     *
     * @return пара ключей центра, не связанная с парами Диффи — Хеллмана
     * @throws GeneralSecurityException если алгоритм подписи недоступен
     */
    public static KeyPair generateSigningAuthorityKeyPair() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    /**
     * Проверяет записи и подписывает справочник секретным ключом центра.
     *
     * <p>Центр получает имена и ключи от доверенного кода подготовки участников.
     * Сама подпись не проверяет личность человека, передавшего ключ центру.</p>
     *
     * @param subgroupParameters предварительно проверенные параметры подгруппы
     * @param publicKeysByParticipant подтверждённые центром имена и открытые ключи
     * @param authorityPrivateKey секретный ключ центра для создания подписи
     * @return подписанный справочник
     * @throws GeneralSecurityException если не удалось создать подпись
     * @throws IllegalArgumentException если справочник пуст, имя или открытый ключ некорректны
     */
    public static SignedPublicKeyDirectory createSignedDirectory(
            GroupParameters subgroupParameters,
            Map<String, BigInteger> publicKeysByParticipant,
            PrivateKey authorityPrivateKey
    ) throws GeneralSecurityException {
        Map<String, BigInteger> confirmedPublicKeys = Map.copyOf(publicKeysByParticipant);
        validateDirectoryEntries(subgroupParameters, confirmedPublicKeys);

        Signature directorySigner = Signature.getInstance("Ed25519");
        directorySigner.initSign(authorityPrivateKey);
        directorySigner.update(encodeDirectoryContent(subgroupParameters, confirmedPublicKeys));

        SignedPublicKeyDirectory signedDirectory = new SignedPublicKeyDirectory(
                subgroupParameters,
                confirmedPublicKeys,
                directorySigner.sign()
        );
        System.out.println("Справочник открытых ключей подписан центром.");
        return signedDirectory;
    }

    /**
     * Проверяет подпись, совместимость подгруппы и записи, затем возвращает открытые ключи.
     *
     * @param expectedSubgroupParameters проверенные параметры текущего обмена
     * @param trustedAuthorityPublicKey заранее доверенный открытый ключ центра
     * @return неизменяемый справочник проверенных открытых ключей участников
     * @throws GeneralSecurityException если подпись неверна или её не удалось проверить
     * @throws IllegalArgumentException если параметры подгруппы или записи некорректны
     */
    public Map<String, BigInteger> verifySignatureAndGetPublicKeys(
            GroupParameters expectedSubgroupParameters,
            PublicKey trustedAuthorityPublicKey
    ) throws GeneralSecurityException {
        Signature directoryVerifier = Signature.getInstance("Ed25519");
        directoryVerifier.initVerify(trustedAuthorityPublicKey);
        directoryVerifier.update(encodeDirectoryContent(subgroupParameters, publicKeysByParticipant));
        if (!directoryVerifier.verify(directorySignature)) {
            throw new SignatureException("Подпись справочника открытых ключей неверна");
        }
        if (!expectedSubgroupParameters.equals(subgroupParameters)) {
            throw new IllegalArgumentException("Справочник создан для других параметров подгруппы");
        }

        validateDirectoryEntries(expectedSubgroupParameters, publicKeysByParticipant);
        System.out.println("Подпись центра и содержимое справочника успешно проверены.");
        return publicKeysByParticipant;
    }

    /**
     * Подготавливает копию параметров, открытых ключей и подписи для сохранения в файл.
     *
     * @return поля справочника; подпись записана в Base64
     */
    public Properties toProperties() {
        Properties directoryProperties = new Properties();
        directoryProperties.setProperty("p", subgroupParameters.primeModulus().toString());
        directoryProperties.setProperty("q", subgroupParameters.subgroupOrder().toString());
        directoryProperties.setProperty("g", subgroupParameters.generator().toString());
        directoryProperties.setProperty("signature", Base64.getEncoder().encodeToString(directorySignature));
        for (Map.Entry<String, BigInteger> participantEntry : publicKeysByParticipant.entrySet()) {
            directoryProperties.setProperty(
                    "participant." + participantEntry.getKey(), participantEntry.getValue().toString()
            );
        }
        return directoryProperties;
    }

    /**
     * Восстанавливает справочник из сохранённых полей, не устанавливая доверие к его содержимому.
     *
     * <p>После восстановления обязательно вызывается verifySignatureAndGetPublicKeys
     * с отдельно полученным доверенным открытым ключом центра.</p>
     *
     * @param directoryProperties сохранённые параметры, открытые ключи и подпись
     * @return справочник, требующий проверки подписи
     * @throws IllegalArgumentException если числа или Base64 имеют неверный формат
     */
    public static SignedPublicKeyDirectory fromProperties(Properties directoryProperties) {
        GroupParameters storedParameters = new GroupParameters(
                new BigInteger(directoryProperties.getProperty("p", "")),
                new BigInteger(directoryProperties.getProperty("q", "")),
                new BigInteger(directoryProperties.getProperty("g", ""))
        );
        Map<String, BigInteger> storedPublicKeys = new HashMap<>();
        for (String propertyName : directoryProperties.stringPropertyNames()) {
            if (propertyName.startsWith("participant.")) {
                String participantName = propertyName.substring("participant.".length());
                storedPublicKeys.put(participantName, new BigInteger(directoryProperties.getProperty(propertyName)));
            }
        }
        byte[] storedSignature = Base64.getDecoder().decode(directoryProperties.getProperty("signature", ""));
        return new SignedPublicKeyDirectory(storedParameters, storedPublicKeys, storedSignature);
    }

    /**
     * Проверяет наличие записей, допустимость имён и принадлежность ключей подгруппе.
     *
     * @param subgroupParameters проверенные параметры подгруппы
     * @param publicKeysByParticipant проверяемые записи
     * @throws IllegalArgumentException если справочник пуст, имя или ключ некорректны
     */
    private static void validateDirectoryEntries(
            GroupParameters subgroupParameters,
            Map<String, BigInteger> publicKeysByParticipant
    ) {
        if (publicKeysByParticipant.isEmpty()) {
            throw new IllegalArgumentException("Справочник открытых ключей не должен быть пустым");
        }

        for (Map.Entry<String, BigInteger> participantEntry : publicKeysByParticipant.entrySet()) {
            String participantName = participantEntry.getKey();
            if (participantName.isBlank() || participantName.contains("=")
                    || participantName.contains("\n") || participantName.contains("\r")) {
                throw new IllegalArgumentException("Имя участника пустое или содержит разделители формата");
            }
            GroupValidator.validateSubgroupPublicKey(subgroupParameters, participantEntry.getValue());
        }
    }

    /**
     * Представляет параметры и записи одними и теми же байтами при подписи и проверке.
     *
     * <p>Сортировка по имени исключает зависимость подписи от порядка обхода Map.</p>
     *
     * @param subgroupParameters параметры, включаемые в подпись
     * @param publicKeysByParticipant записи, включаемые в подпись
     * @return содержимое справочника в UTF-8 с фиксированным порядком записей
     */
    private static byte[] encodeDirectoryContent(
            GroupParameters subgroupParameters,
            Map<String, BigInteger> publicKeysByParticipant
    ) {
        StringBuilder directoryContent = new StringBuilder("MQV public key directory v1\n");
        directoryContent.append("p=").append(subgroupParameters.primeModulus()).append('\n');
        directoryContent.append("q=").append(subgroupParameters.subgroupOrder()).append('\n');
        directoryContent.append("g=").append(subgroupParameters.generator()).append("\n\n");

        Map<String, BigInteger> sortedPublicKeys = new TreeMap<>(publicKeysByParticipant);
        for (Map.Entry<String, BigInteger> participantEntry : sortedPublicKeys.entrySet()) {
            directoryContent.append(participantEntry.getKey()).append('=')
                    .append(participantEntry.getValue()).append('\n');
        }

        return directoryContent.toString().getBytes(StandardCharsets.UTF_8);
    }
}
