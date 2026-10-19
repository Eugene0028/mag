package securetransfer.rgz.measurement;

import securetransfer.common.core.DiffieHellman;
import securetransfer.common.core.GroupParameters;
import securetransfer.common.core.Mqv;
import securetransfer.common.core.ParticipantKeyPair;
import securetransfer.common.core.SignedPublicKeyDirectory;
import securetransfer.common.measurement.BenchmarkRunner;
import securetransfer.common.measurement.KeyAgreementBenchmark;
import securetransfer.common.measurement.StageMeasurement;
import securetransfer.rgz.key.MqvKeyDerivation;
import securetransfer.rgz.key.Sha3Hasher;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Сравнение SHA3-256 и получения ключа с математическими операциями MQV в одной JVM. */
public final class KeyDerivationBenchmark {
    /** Название отдельного преобразования числа в байты. */
    public static final String ENCODING_STAGE = "Представление секрета в байтах";

    /** Название хеширования уже подготовленных байтов секрета. */
    public static final String HASH_STAGE = "Хеширование готовых байтов";

    /** Название полного преобразования готового секрета в ключ шифра. */
    public static final String DERIVATION_STAGE = "Получение ключа из готового секрета";

    /** Название вычисления секрета MQV и последующего получения ключа. */
    public static final String COMPLETE_STAGE = "Вычисление секрета MQV и получение ключа";

    /** Количество реальных секретов MQV, используемых по кругу в измерениях. */
    private static final int PREPARED_EXCHANGE_COUNT = 64;

    /** Максимальное число попыток получить невырожденный секрет одного обмена. */
    private static final int MAX_PREPARATION_ATTEMPTS = 1000;

    /** Общий исполнитель прогрева и чтения циклов потока. */
    private final BenchmarkRunner measurementRunner;

    /** Хеш-функция; поиск провайдера выполняется до замеров. */
    private final Sha3Hasher hashFunction;

    /**
     * Подключает исполнитель и хеш-функцию вызывающего потока.
     *
     * @param measurementRunner исполнитель замеров
     * @param hashFunction подготовленная SHA3-256
     */
    public KeyDerivationBenchmark(BenchmarkRunner measurementRunner, Sha3Hasher hashFunction) {
        this.measurementRunner = Objects.requireNonNull(measurementRunner);
        this.hashFunction = Objects.requireNonNull(hashFunction);
    }

    /**
     * Измеряет три операции MQV и четыре этапа обработки общего секрета.
     *
     * @param subgroupParameters проверенные параметры подгруппы текущего размера
     * @param keyLengthBytes требуемая длина ключа шифра
     * @return семь этапов в процессорных циклах на операцию
     * @throws GeneralSecurityException если подготовка подписанного справочника не удалась
     */
    public List<StageMeasurement> measureKeyDerivation(GroupParameters subgroupParameters, int keyLengthBytes)
            throws GeneralSecurityException {
        List<StageMeasurement> measurements = new ArrayList<>(
                new KeyAgreementBenchmark(measurementRunner).measureMqv(subgroupParameters)
        );
        System.out.println("\nПодготовка секретов MQV и их байтов вне замеров хеширования.");
        MqvKeyDerivation keyDerivation = new MqvKeyDerivation(subgroupParameters, hashFunction);
        ParticipantKeyPair ownLongTermKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
        ParticipantKeyPair otherLongTermKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
        KeyPair authorityKeyPair = SignedPublicKeyDirectory.generateSigningAuthorityKeyPair();
        SignedPublicKeyDirectory directory = SignedPublicKeyDirectory.createSignedDirectory(
                subgroupParameters,
                Map.of("Alice", ownLongTermKeyPair.publicKey(), "Bob", otherLongTermKeyPair.publicKey()),
                authorityKeyPair.getPrivate()
        );
        BigInteger trustedOtherParticipantPublicKey = directory.verifySignatureAndGetPublicKeys(
                subgroupParameters, authorityKeyPair.getPublic()
        ).get("Bob");
        PreparedExchange[] preparedExchanges = prepareExchanges(
                subgroupParameters, ownLongTermKeyPair, trustedOtherParticipantPublicKey, keyDerivation
        );

        measurements.add(measurementRunner.measureStage("MQV → байты", ENCODING_STAGE, operationIndex -> {
            PreparedExchange exchange = preparedExchanges[operationIndex % preparedExchanges.length];
            return keyDerivation.encodeSharedSecret(exchange.sharedSecret());
        }));
        measurements.add(measurementRunner.measureStage(Sha3Hasher.ALGORITHM_NAME, HASH_STAGE, operationIndex -> {
            PreparedExchange exchange = preparedExchanges[operationIndex % preparedExchanges.length];
            return hashFunction.calculateHash(exchange.encodedSharedSecret());
        }));
        measurements.add(measurementRunner.measureStage("MQV → ключ", DERIVATION_STAGE, operationIndex -> {
            PreparedExchange exchange = preparedExchanges[operationIndex % preparedExchanges.length];
            return keyDerivation.deriveKey(exchange.sharedSecret(), keyLengthBytes);
        }));
        measurements.add(measurementRunner.measureStage("MQV + SHA3-256", COMPLETE_STAGE, operationIndex -> {
            PreparedExchange exchange = preparedExchanges[operationIndex % preparedExchanges.length];
            BigInteger sharedSecret = Mqv.calculateSharedSecret(
                    subgroupParameters, ownLongTermKeyPair.privateKey(), exchange.ownEphemeralKeyPair(),
                    trustedOtherParticipantPublicKey, exchange.otherEphemeralPublicKey()
            );
            return keyDerivation.deriveKey(sharedSecret, keyLengthBytes);
        }));
        return List.copyOf(measurements);
    }

    /**
     * Готовит реальные секреты и временные ключи MQV, отбрасывая вырожденные результаты.
     *
     * @param subgroupParameters проверенные параметры подгруппы
     * @param ownLongTermKeyPair долговременная пара своего участника
     * @param trustedOtherParticipantPublicKey подтверждённый ключ собеседника
     * @param keyDerivation преобразование секрета в единый формат
     * @return наборы для всех измеряемых этапов
     */
    private PreparedExchange[] prepareExchanges(
            GroupParameters subgroupParameters,
            ParticipantKeyPair ownLongTermKeyPair,
            BigInteger trustedOtherParticipantPublicKey,
            MqvKeyDerivation keyDerivation
    ) {
        PreparedExchange[] exchanges = new PreparedExchange[PREPARED_EXCHANGE_COUNT];
        for (int exchangeIndex = 0; exchangeIndex < exchanges.length; exchangeIndex++) {
            for (int attemptIndex = 0; attemptIndex < MAX_PREPARATION_ATTEMPTS; attemptIndex++) {
                ParticipantKeyPair ownEphemeralKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
                ParticipantKeyPair otherEphemeralKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
                BigInteger sharedSecret;
                try {
                    sharedSecret = Mqv.calculateSharedSecret(
                            subgroupParameters, ownLongTermKeyPair.privateKey(), ownEphemeralKeyPair,
                            trustedOtherParticipantPublicKey, otherEphemeralKeyPair.publicKey()
                    );
                } catch (IllegalStateException invalidSharedSecret) {
                    continue;
                }
                exchanges[exchangeIndex] = new PreparedExchange(
                        ownEphemeralKeyPair, otherEphemeralKeyPair.publicKey(), sharedSecret,
                        keyDerivation.encodeSharedSecret(sharedSecret)
                );
                break;
            }
            if (exchanges[exchangeIndex] == null) {
                throw new IllegalStateException("Не удалось подготовить допустимый общий секрет MQV");
            }
        }
        return exchanges;
    }

    /**
     * Входы одного обмена, которые существуют только в памяти текущего запуска.
     *
     * @param ownEphemeralKeyPair собственная временная пара
     * @param otherEphemeralPublicKey временный открытый ключ собеседника
     * @param sharedSecret заранее вычисленный общий секрет
     * @param encodedSharedSecret его представление фиксированной длины
     */
    private record PreparedExchange(
            ParticipantKeyPair ownEphemeralKeyPair,
            BigInteger otherEphemeralPublicKey,
            BigInteger sharedSecret,
            byte[] encodedSharedSecret
    ) {
    }
}
