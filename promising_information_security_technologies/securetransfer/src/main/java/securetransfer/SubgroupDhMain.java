package securetransfer;

import securetransfer.core.DiffieHellman;
import securetransfer.core.GroupParameters;
import securetransfer.core.ParticipantKeyPair;
import securetransfer.protocol.KeyAgreementProtocol;
import securetransfer.protocol.SubgroupDhProtocol;
import securetransfer.storage.GroupParameterFileStorage;

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
        Path subgroupParametersFile = Path.of("data", "params", "subgroup.properties");
        // Маленькие числа помогают проследить вычисления в учебном примере.
        int modulusBitLength = 16;
        int subgroupOrderBitLength = 8;
        GroupParameters subgroupParameters = GroupParameterFileStorage.loadOrGenerateSubgroupParameters(
                subgroupParametersFile,
                modulusBitLength,
                subgroupOrderBitLength
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
