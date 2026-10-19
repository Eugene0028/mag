package securetransfer.lab1;

import securetransfer.common.core.DiffieHellman;
import securetransfer.common.core.GroupParameters;
import securetransfer.common.core.ParticipantKeyPair;
import securetransfer.common.protocol.KeyAgreementProtocol;
import securetransfer.common.protocol.SubgroupDhProtocol;
import securetransfer.common.storage.GroupParameterFileStorage;
import securetransfer.lab1.config.Lab1Settings;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;

/**
 * Сценарий локального обмена Диффи — Хеллмана в подгруппе простого порядка q.
 *
 * <p>Загружает сохранённые параметры или создаёт их при первом запуске.</p>
 *
 * Подгруппа: g порождает q элементов.
 */
public class SubgroupDhMain {
    /**
     * Подготавливает параметры подгруппы, создаёт ключи Алисы и Боба и вычисляет общие секреты.
     *
     * @param commandLineArguments аргументы запуска; пока не используются
     * @throws IOException если файл параметров не удалось прочитать или сохранить
     */
    public static void main(String[] commandLineArguments) throws IOException {
        Path subgroupParametersFile = Lab1Settings.DATA_PATHS.subgroupParametersFile();
        GroupParameters subgroupParameters = GroupParameterFileStorage.loadOrGenerateSubgroupParameters(
                subgroupParametersFile,
                Lab1Settings.DEMONSTRATION_GROUP_SETTINGS.modulusBitLength(),
                Lab1Settings.DEMONSTRATION_GROUP_SETTINGS.subgroupOrderBitLength()
        );

        System.out.printf("Параметры подгруппы: p = %s, q = %s, g = %s%n",
                subgroupParameters.primeModulus(),
                subgroupParameters.subgroupOrder(),
                subgroupParameters.generator());

        ParticipantKeyPair aliceKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
        ParticipantKeyPair bobKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);

        KeyAgreementProtocol aliceProtocol = new SubgroupDhProtocol(subgroupParameters, aliceKeyPair);
        KeyAgreementProtocol bobProtocol = new SubgroupDhProtocol(subgroupParameters, bobKeyPair);

        // Вывод секретных ключей позволяет проследить вычисления в учебном примере.
        System.out.printf("Алиса: секрет a = %s, открытый ключ A = %s%n",
                aliceKeyPair.privateKey(), aliceKeyPair.publicKey());
        System.out.printf("Боб: секрет b = %s, открытый ключ B = %s%n",
                bobKeyPair.privateKey(), bobKeyPair.publicKey());

        BigInteger aliceSharedSecret = aliceProtocol.calculateSharedSecret(
                bobProtocol.getPublicExchangeData()
        );

        BigInteger bobSharedSecret = bobProtocol.calculateSharedSecret(
                aliceProtocol.getPublicExchangeData()
        );

        System.out.println("Секрет Алисы B^a mod p = " + aliceSharedSecret);
        System.out.println("Секрет Боба A^b mod p = " + bobSharedSecret);
        System.out.println("Общие секреты совпадают: " + aliceSharedSecret.equals(bobSharedSecret));
    }
}
