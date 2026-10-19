package securetransfer.measurement;

import securetransfer.core.DiffieHellman;
import securetransfer.core.GroupParameters;
import securetransfer.core.Mqv;
import securetransfer.core.ParticipantKeyPair;
import securetransfer.core.SignedPublicKeyDirectory;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Готовит входные данные и измеряет математические этапы трёх протоколов.
 * Параметры, файлы, справочник MQV и проверка результатов готовятся вне измеряемых интервалов.
 */
public final class KeyAgreementBenchmark {
    /** Количество заранее подготовленных наборов входа, используемых по кругу. */
    private static final int PREPARED_EXCHANGE_COUNT = 64;

    /** Ограничение повторного выбора временных ключей MQV при вырожденном общем секрете. */
    private static final int MAX_MQV_PREPARATION_ATTEMPTS = 1000;

    /** Исполнитель прогрева и чтения процессорных циклов. */
    private final BenchmarkRunner measurementRunner;

    /**
     * Связывает сценарии алгоритмов с общим исполнителем замеров.
     *
     * @param measurementRunner исполнитель в текущем потоке
     */
    public KeyAgreementBenchmark(BenchmarkRunner measurementRunner) {
        this.measurementRunner = measurementRunner;
    }

    /**
     * Выполняет три этапа DH в полной группе.
     *
     * @param groupParameters предварительно проверенные параметры полной группы
     * @return показания генерации секрета, открытого ключа и общего секрета
     */
    public List<StageMeasurement> measureFullGroupDh(GroupParameters groupParameters) {
        return measureDiffieHellman(
                "DH в полной группе", groupParameters,
                () -> DiffieHellman.generateFullGroupPrivateKey(groupParameters),
                () -> DiffieHellman.generateKeyPair(groupParameters)
        );
    }

    /**
     * Выполняет три этапа DH в подгруппе, общей с измерением MQV.
     *
     * @param subgroupParameters предварительно проверенные параметры подгруппы
     * @return показания трёх этапов DH
     */
    public List<StageMeasurement> measureSubgroupDh(GroupParameters subgroupParameters) {
        return measureDiffieHellman(
                "DH в подгруппе", subgroupParameters,
                () -> DiffieHellman.generateSubgroupPrivateKey(subgroupParameters),
                () -> DiffieHellman.generateSubgroupKeyPair(subgroupParameters)
        );
    }

    /**
     * Измеряет генерацию временного секрета, временного открытого ключа и общего секрета MQV.
     * Долговременные пары и подписанный справочник создаются один раз до измерений.
     *
     * @param subgroupParameters те же параметры, что использовались для DH в подгруппе
     * @return показания трёх этапов сеансового обмена MQV
     * @throws GeneralSecurityException если создание или проверка справочника не удались
     */
    public List<StageMeasurement> measureMqv(GroupParameters subgroupParameters)
            throws GeneralSecurityException {
        System.out.println("\nПодготовка долговременных ключей и справочника MQV вне замеров.");
        ParticipantKeyPair ownLongTermKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
        ParticipantKeyPair otherLongTermKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
        KeyPair authorityKeyPair = SignedPublicKeyDirectory.generateSigningAuthorityKeyPair();
        SignedPublicKeyDirectory directory = SignedPublicKeyDirectory.createSignedDirectory(
                subgroupParameters,
                Map.of("Alice", ownLongTermKeyPair.publicKey(), "Bob", otherLongTermKeyPair.publicKey()),
                authorityKeyPair.getPrivate()
        );
        Map<String, BigInteger> verifiedPublicKeys = directory.verifySignatureAndGetPublicKeys(
                subgroupParameters, authorityKeyPair.getPublic()
        );
        BigInteger trustedOtherParticipantPublicKey = verifiedPublicKeys.get("Bob");

        ExchangeKeyPairs[] preparedExchanges = new ExchangeKeyPairs[PREPARED_EXCHANGE_COUNT];
        for (int exchangeIndex = 0; exchangeIndex < preparedExchanges.length; exchangeIndex++) {
            preparedExchanges[exchangeIndex] = prepareValidMqvExchange(
                    subgroupParameters, ownLongTermKeyPair, otherLongTermKeyPair
            );
        }

        List<StageMeasurement> measurements = measureKeyGeneration(
                "MQV", subgroupParameters,
                () -> DiffieHellman.generateSubgroupPrivateKey(subgroupParameters), preparedExchanges
        );
        measurements.add(measurementRunner.measureStage("MQV", "Вычисление общего секрета", operationIndex -> {
            ExchangeKeyPairs exchange = preparedExchanges[operationIndex % preparedExchanges.length];
            return Mqv.calculateSharedSecret(
                    subgroupParameters, ownLongTermKeyPair.privateKey(), exchange.ownKeyPair(),
                    trustedOtherParticipantPublicKey, exchange.otherKeyPair().publicKey()
            );
        }));
        return List.copyOf(measurements);
    }

    /**
     * Готовит меняющиеся ключи обоих участников и измеряет математические этапы DH.
     *
     * @param algorithmName название варианта DH
     * @param groupParameters проверенные параметры выбранной группы
     * @param privateKeyGenerator способ выбора секрета в диапазоне выбранной группы
     * @param keyPairGenerator способ подготовки полного набора входных ключей
     * @return три набора показаний
     */
    private List<StageMeasurement> measureDiffieHellman(
            String algorithmName,
            GroupParameters groupParameters,
            Supplier<BigInteger> privateKeyGenerator,
            Supplier<ParticipantKeyPair> keyPairGenerator
    ) {
        System.out.println("\nПодготовка входных ключей: " + algorithmName + " (вне замеров).");
        ExchangeKeyPairs[] preparedExchanges = new ExchangeKeyPairs[PREPARED_EXCHANGE_COUNT];
        for (int exchangeIndex = 0; exchangeIndex < preparedExchanges.length; exchangeIndex++) {
            ParticipantKeyPair ownKeyPair = keyPairGenerator.get();
            ParticipantKeyPair otherKeyPair = keyPairGenerator.get();
            BigInteger ownSharedSecret = DiffieHellman.calculateSharedSecret(
                    groupParameters, ownKeyPair.privateKey(), otherKeyPair.publicKey()
            );
            BigInteger otherSharedSecret = DiffieHellman.calculateSharedSecret(
                    groupParameters, otherKeyPair.privateKey(), ownKeyPair.publicKey()
            );
            if (!ownSharedSecret.equals(otherSharedSecret)) {
                throw new IllegalStateException("Общие секреты DH при подготовке замеров не совпали.");
            }
            preparedExchanges[exchangeIndex] = new ExchangeKeyPairs(ownKeyPair, otherKeyPair);
        }

        List<StageMeasurement> measurements = measureKeyGeneration(
                algorithmName, groupParameters, privateKeyGenerator, preparedExchanges
        );
        measurements.add(measurementRunner.measureStage(
                algorithmName, "Вычисление общего секрета", operationIndex -> {
                    ExchangeKeyPairs exchange = preparedExchanges[operationIndex % preparedExchanges.length];
                    return DiffieHellman.calculateSharedSecret(
                            groupParameters, exchange.ownKeyPair().privateKey(), exchange.otherKeyPair().publicKey()
                    );
                }
        ));
        return List.copyOf(measurements);
    }

    /**
     * Раздельно измеряет случайный выбор секретного ключа и вычисление открытого.
     *
     * @param algorithmName название алгоритма; для MQV измеряются временные ключи
     * @param groupParameters проверенные параметры группы
     * @param privateKeyGenerator способ выбора нового секрета
     * @param preparedExchanges заранее подготовленные секреты для вычисления открытых ключей
     * @return изменяемый список двух результатов, к которому вызывающий код добавляет третий
     */
    private List<StageMeasurement> measureKeyGeneration(
            String algorithmName,
            GroupParameters groupParameters,
            Supplier<BigInteger> privateKeyGenerator,
            ExchangeKeyPairs[] preparedExchanges
    ) {
        List<StageMeasurement> measurements = new ArrayList<>();
        measurements.add(measurementRunner.measureStage(
                algorithmName, "Генерация секретного ключа", operationIndex -> privateKeyGenerator.get()
        ));
        measurements.add(measurementRunner.measureStage(
                algorithmName, "Вычисление открытого ключа", operationIndex -> {
                    ParticipantKeyPair ownKeyPair = preparedExchanges[operationIndex % preparedExchanges.length]
                            .ownKeyPair();
                    return DiffieHellman.calculatePublicKey(groupParameters, ownKeyPair.privateKey());
                }
        ));
        return measurements;
    }

    /**
     * Готовит успешный обмен MQV, отбрасывая редкие вырожденные временные пары.
     * Совпадение секретов проверяется здесь, за пределами измеряемого этапа.
     *
     * @param subgroupParameters проверенные параметры подгруппы
     * @param ownLongTermKeyPair долговременная пара Алисы
     * @param otherLongTermKeyPair долговременная пара Боба
     * @return временные пары для успешного обмена
     */
    private ExchangeKeyPairs prepareValidMqvExchange(
            GroupParameters subgroupParameters,
            ParticipantKeyPair ownLongTermKeyPair,
            ParticipantKeyPair otherLongTermKeyPair
    ) {
        for (int attemptIndex = 0; attemptIndex < MAX_MQV_PREPARATION_ATTEMPTS; attemptIndex++) {
            ParticipantKeyPair ownEphemeralKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
            ParticipantKeyPair otherEphemeralKeyPair = DiffieHellman.generateSubgroupKeyPair(subgroupParameters);
            BigInteger ownSharedSecret;
            try {
                ownSharedSecret = Mqv.calculateSharedSecret(
                        subgroupParameters, ownLongTermKeyPair.privateKey(), ownEphemeralKeyPair,
                        otherLongTermKeyPair.publicKey(), otherEphemeralKeyPair.publicKey()
                );
            } catch (IllegalStateException invalidSharedSecret) {
                continue;
            }
            BigInteger otherSharedSecret = Mqv.calculateSharedSecret(
                    subgroupParameters, otherLongTermKeyPair.privateKey(), otherEphemeralKeyPair,
                    ownLongTermKeyPair.publicKey(), ownEphemeralKeyPair.publicKey()
            );
            if (!ownSharedSecret.equals(otherSharedSecret)) {
                throw new IllegalStateException("Общие секреты MQV при подготовке замеров не совпали.");
            }
            return new ExchangeKeyPairs(ownEphemeralKeyPair, otherEphemeralKeyPair);
        }
        throw new IllegalStateException("Не удалось подготовить допустимые временные пары MQV.");
    }

    /**
     * Две пары участников одного заранее подготовленного обмена.
     *
     * @param ownKeyPair собственная пара; для MQV — временная
     * @param otherKeyPair пара собеседника; для MQV — временная
     */
    private record ExchangeKeyPairs(ParticipantKeyPair ownKeyPair, ParticipantKeyPair otherKeyPair) {
    }
}
