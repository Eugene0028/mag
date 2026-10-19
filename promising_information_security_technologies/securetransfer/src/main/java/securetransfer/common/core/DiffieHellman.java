package securetransfer.common.core;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Создание ключей и вычисление общего секрета Диффи — Хеллмана в полной группе и подгруппе.
 */
public final class DiffieHellman {
    /**
     * Источник случайных чисел для секретных показателей участников.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Служебный конструктор класса статических операций.
     */
    private DiffieHellman() {
    }

    /**
     * Выбирает случайный секретный показатель и вычисляет соответствующий открытый ключ.
     *
     * <p>Секрет выбирается в диапазоне от 1 до p − 2 включительно.
     * Значение q исключается: оно даёт открытый ключ p − 1 порядка 2.
     * Открытый ключ вычисляется как g^privateKey mod p.</p>
     *
     * @param groupParameters проверенные параметры полной группы с p = 2q + 1
     * @return новая пара из секретного показателя и открытого ключа
     * @throws IllegalArgumentException если модуль p не больше 2
     */
    public static ParticipantKeyPair generateKeyPair(GroupParameters groupParameters) {
        BigInteger privateKey = generateFullGroupPrivateKey(groupParameters);
        BigInteger publicKey = calculatePublicKey(groupParameters, privateKey);
        return new ParticipantKeyPair(privateKey, publicKey);
    }

    /**
     * Выбирает секрет полной группы в диапазоне 1…p − 2, исключая q.
     *
     * @param groupParameters проверенные параметры полной группы
     * @return случайный секретный ключ, ещё без вычисления открытого
     * @throws IllegalArgumentException если p не больше 2
     */
    public static BigInteger generateFullGroupPrivateKey(GroupParameters groupParameters) {
        if (groupParameters.primeModulus().compareTo(BigInteger.TWO) <= 0) {
            throw new IllegalArgumentException("Модуль p должен быть больше 2");
        }

        BigInteger fullGroupOrder = groupParameters.primeModulus().subtract(BigInteger.ONE);

        BigInteger privateKey;
        do {
            privateKey = new BigInteger(fullGroupOrder.bitLength(), SECURE_RANDOM);
        } while (privateKey.signum() == 0
                || privateKey.compareTo(fullGroupOrder) >= 0
                || privateKey.equals(groupParameters.subgroupOrder()));

        return privateKey;
    }

    /**
     * Создаёт пару ключей для обмена в подгруппе простого порядка q.
     *
     * <p>Секрет выбирается в диапазоне от 1 до q − 1 включительно.
     * Открытый ключ вычисляется как g^privateKey mod p.</p>
     *
     * @param subgroupParameters предварительно проверенные параметры подгруппы
     * @return новая пара из секретного и открытого ключей участника
     * @throws IllegalArgumentException если порядок q меньше 2
     */
    public static ParticipantKeyPair generateSubgroupKeyPair(GroupParameters subgroupParameters) {
        BigInteger privateKey = generateSubgroupPrivateKey(subgroupParameters);
        BigInteger publicKey = calculatePublicKey(subgroupParameters, privateKey);
        return new ParticipantKeyPair(privateKey, publicKey);
    }

    /**
     * Выбирает секрет подгруппы в диапазоне 1…q − 1.
     *
     * @param subgroupParameters проверенные параметры подгруппы
     * @return случайный секретный ключ, ещё без вычисления открытого
     * @throws IllegalArgumentException если q меньше 2
     */
    public static BigInteger generateSubgroupPrivateKey(GroupParameters subgroupParameters) {
        BigInteger subgroupOrder = subgroupParameters.subgroupOrder();
        if (subgroupOrder.compareTo(BigInteger.TWO) < 0) {
            throw new IllegalArgumentException("Порядок q должен быть не меньше 2");
        }

        BigInteger privateKey;
        do {
            privateKey = new BigInteger(subgroupOrder.bitLength(), SECURE_RANDOM);
        } while (privateKey.signum() == 0 || privateKey.compareTo(subgroupOrder) >= 0);

        return privateKey;
    }

    /**
     * Вычисляет открытый ключ g^privateKey mod p для полной группы или подгруппы.
     *
     * @param groupParameters предварительно проверенные общие параметры
     * @param privateKey секретный ключ из допустимого диапазона выбранной группы
     * @return соответствующий открытый ключ
     */
    public static BigInteger calculatePublicKey(GroupParameters groupParameters, BigInteger privateKey) {
        return groupParameters.generator().modPow(privateKey, groupParameters.primeModulus());
    }

    /**
     * Вычисляет общий секрет из своего секретного ключа и чужого открытого ключа.
     *
     * <p>Алиса вычисляет B^a mod p, Боб — A^b mod p.
     * При одинаковых параметрах группы оба получают g^(a·b) mod p.
     * Вызывающий код предварительно проверяет параметры группы и открытый ключ.</p>
     *
     * @param groupParameters           общие параметры группы участников
     * @param privateKey                собственный секретный ключ участника
     * @param otherParticipantPublicKey открытый ключ другого участника
     * @return общий секрет в виде числа, без хэширования
     */
    public static BigInteger calculateSharedSecret(
            GroupParameters groupParameters,
            BigInteger privateKey,
            BigInteger otherParticipantPublicKey
    ) {
        return otherParticipantPublicKey.modPow(privateKey, groupParameters.primeModulus());
    }
}
