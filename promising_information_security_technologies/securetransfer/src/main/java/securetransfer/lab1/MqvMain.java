package securetransfer.lab1;

import securetransfer.common.core.DiffieHellman;
import securetransfer.common.core.GroupParameters;
import securetransfer.common.core.ParticipantKeyPair;
import securetransfer.common.core.SignedPublicKeyDirectory;
import securetransfer.common.protocol.KeyAgreementProtocol;
import securetransfer.common.protocol.MqvProtocol;
import securetransfer.common.storage.GroupParameterFileStorage;
import securetransfer.common.storage.ParticipantKeyPairFileStorage;
import securetransfer.lab1.config.Lab1Settings;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Map;

/**
 * Локальный обмен MQV с подписанным справочником открытых ключей.
 *
 * <p>Пары Алисы и Боба сохраняются между запусками.
 * Временные пары, учебный центр и подписанный справочник создаются в памяти при каждом запуске.</p>
 */
public class MqvMain {
    /**
     * Подготавливает ключи, проверяет справочник и сравнивает общие секреты Алисы и Боба.
     *
     * @param commandLineArguments аргументы запуска; пока не используются
     * @throws IOException если параметры или пары ключей не удалось прочитать либо сохранить
     * @throws GeneralSecurityException если справочник не удалось подписать или проверить
     * @throws IllegalStateException если MQV требует повторить обмен с новыми временными парами
     */
    public static void main(String[] commandLineArguments) throws IOException, GeneralSecurityException {
        Path subgroupParametersFile = Lab1Settings.DATA_PATHS.subgroupParametersFile();
        GroupParameters subgroupParameters = GroupParameterFileStorage.loadOrGenerateSubgroupParameters(
                subgroupParametersFile,
                Lab1Settings.DEMONSTRATION_GROUP_SETTINGS.modulusBitLength(),
                Lab1Settings.DEMONSTRATION_GROUP_SETTINGS.subgroupOrderBitLength()
        );

        ParticipantKeyPair aliceLongTermKeyPair = loadOrCreateLongTermKeyPair(
                subgroupParameters,
                Lab1Settings.DATA_PATHS.aliceLongTermKeyFile()
        );
        ParticipantKeyPair bobLongTermKeyPair = loadOrCreateLongTermKeyPair(
                subgroupParameters,
                Lab1Settings.DATA_PATHS.bobLongTermKeyFile()
        );

        System.out.println("Долговременный открытый ключ Алисы A = " + aliceLongTermKeyPair.publicKey());
        System.out.println("Долговременный открытый ключ Боба B = " + bobLongTermKeyPair.publicKey());
        System.out.println("Долговременные ключи подготовлены.");

        // Подготовка центра: его секретный ключ используется только для подписи справочника.
        KeyPair signingAuthorityKeyPair = SignedPublicKeyDirectory.generateSigningAuthorityKeyPair();
        SignedPublicKeyDirectory signedDirectory = SignedPublicKeyDirectory.createSignedDirectory(
                subgroupParameters,
                Map.of("Alice", aliceLongTermKeyPair.publicKey(), "Bob", bobLongTermKeyPair.publicKey()),
                signingAuthorityKeyPair.getPrivate()
        );

        // В локальном примере доверенный ключ центра передаётся обоим участникам напрямую.
        PublicKey trustedAuthorityPublicKey = signingAuthorityKeyPair.getPublic();

        System.out.println("Алиса проверяет справочник:");
        Map<String, BigInteger> aliceVerifiedPublicKeys = signedDirectory.verifySignatureAndGetPublicKeys(
                subgroupParameters,
                trustedAuthorityPublicKey
        );
        System.out.println("Подтверждённый открытый ключ Боба: " + aliceVerifiedPublicKeys.get("Bob"));

        System.out.println("Боб проверяет справочник:");
        Map<String, BigInteger> bobVerifiedPublicKeys = signedDirectory.verifySignatureAndGetPublicKeys(
                subgroupParameters,
                trustedAuthorityPublicKey
        );
        System.out.println("Подтверждённый открытый ключ Алисы: " + bobVerifiedPublicKeys.get("Alice"));

        // Временные пары используются только в текущем обмене и не сохраняются в файлы.
        ParticipantKeyPair aliceEphemeralKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
        ParticipantKeyPair bobEphemeralKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);

        KeyAgreementProtocol aliceProtocol = new MqvProtocol(
                subgroupParameters,
                aliceLongTermKeyPair,
                aliceEphemeralKeyPair,
                aliceVerifiedPublicKeys.get("Bob")
        );
        KeyAgreementProtocol bobProtocol = new MqvProtocol(
                subgroupParameters,
                bobLongTermKeyPair,
                bobEphemeralKeyPair,
                bobVerifiedPublicKeys.get("Alice")
        );

        System.out.println("Временный открытый ключ Алисы X = " + aliceEphemeralKeyPair.publicKey());
        System.out.println("Временный открытый ключ Боба Y = " + bobEphemeralKeyPair.publicKey());
        System.out.println("Временные пары ключей подготовлены для текущего обмена.");

        BigInteger aliceSharedSecret = aliceProtocol.calculateSharedSecret(
                bobProtocol.getPublicExchangeData()
        );
        BigInteger bobSharedSecret = bobProtocol.calculateSharedSecret(
                aliceProtocol.getPublicExchangeData()
        );

        // Вывод общего секрета позволяет проследить вычисления в учебном примере.
        System.out.println("Общий секрет MQV Алисы: " + aliceSharedSecret);
        System.out.println("Общий секрет MQV Боба: " + bobSharedSecret);
        System.out.println("Общие секреты совпадают: " + aliceSharedSecret.equals(bobSharedSecret));
    }

    /**
     * Загружает сохранённую пару или создаёт её, если файла ещё нет.
     *
     * <p>Долговременная пара использует ту же формулу g^privateKey mod p,
     * что и обычная пара Диффи — Хеллмана в подгруппе.</p>
     *
     * @param subgroupParameters проверенные параметры подгруппы
     * @param keyPairFilePath путь к долговременной паре конкретного участника
     * @return проверенная существующая или новая пара ключей
     * @throws IOException если файл не удалось прочитать либо сохранить или сохранённая пара неверна
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
