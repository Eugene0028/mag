package securetransfer;

import securetransfer.core.DiffieHellman;
import securetransfer.core.GroupParameters;
import securetransfer.core.ParticipantKeyPair;
import securetransfer.protocol.FullGroupDhProtocol;
import securetransfer.protocol.KeyAgreementProtocol;
import securetransfer.storage.GroupParameterFileStorage;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;

/**
 * Локальный обмен Диффи — Хеллмана в полной мультипликативной группе.
 *
 * <p>Загружает сохранённые параметры или создаёт их при первом запуске.</p>
 *
 * Полная группа: g порождает p − 1 элементов.
 */
public class FullGroupDhMain {
    /**
     * Подготавливает параметры, создаёт ключи Алисы и Боба и вычисляет общие секреты.
     *
     * @param commandLineArguments аргументы запуска; пока не используются
     * @throws IOException если файл параметров не удалось прочитать или сохранить
     */
    public static void main(String[] commandLineArguments) throws IOException {
        Path fullGroupParametersFile = Path.of("data", "params", "full-group.properties");
        // Маленькие числа помогают проследить вычисления в учебном примере.
        int modulusBitLength = 16;
        GroupParameters groupParameters = GroupParameterFileStorage.loadOrGenerateFullGroupParameters(
                fullGroupParametersFile,
                modulusBitLength
        );

        System.out.printf("Параметры полной группы: p = %s, q = %s, g = %s%n",
                groupParameters.primeModulus(),
                groupParameters.subgroupOrder(),
                groupParameters.generator());

        ParticipantKeyPair aliceKeyPair = DiffieHellman.generateKeyPair(groupParameters);
        ParticipantKeyPair bobKeyPair = DiffieHellman.generateKeyPair(groupParameters);

        KeyAgreementProtocol aliceProtocol = new FullGroupDhProtocol(groupParameters, aliceKeyPair);
        KeyAgreementProtocol bobProtocol = new FullGroupDhProtocol(groupParameters, bobKeyPair);

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
