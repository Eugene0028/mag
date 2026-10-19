package securetransfer.core;

import java.math.BigInteger;

/**
 * Проверка параметров группы и открытых ключей перед обменом секретом.
 *
 * <p>Публичные методы выводят сообщение, если все проверки успешно пройдены.
 * При нарушении условий выбрасывают IllegalArgumentException с описанием ошибки.</p>
 */
public final class GroupValidator {
    /**
     * Параметр достоверности вероятностных проверок простоты.
     */
    private static final int PRIMALITY_TEST_CERTAINTY = 128;

    /**
     * Служебный конструктор класса статических проверок.
     */
    private GroupValidator() {
    }

    /**
     * Проверяет простоту p и q, равенство p = 2q + 1 и полный порядок генератора g.
     *
     * @param groupParameters параметры полной группы
     * @throws IllegalArgumentException если числа или генератор не удовлетворяют условиям
     */
    public static void validateFullGroupParameters(GroupParameters groupParameters) {
        validatePrimeNumbers(groupParameters);

        BigInteger expectedPrimeModulus = groupParameters.subgroupOrder()
                .multiply(BigInteger.TWO).add(BigInteger.ONE);
        if (!groupParameters.primeModulus().equals(expectedPrimeModulus)) {
            throw new IllegalArgumentException("Для полной группы требуется p = 2q + 1");
        }

        BigInteger generator = groupParameters.generator();
        validateElementRange(groupParameters, generator, "Генератор g");
        if (generator.modPow(BigInteger.TWO, groupParameters.primeModulus()).equals(BigInteger.ONE)
                || generator.modPow(groupParameters.subgroupOrder(), groupParameters.primeModulus())
                .equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("Генератор g должен иметь порядок p − 1");
        }

        System.out.println("Параметры полной группы успешно проверены.");
    }

    /**
     * Проверяет простоту p и q, делимость p − 1 на q и порядок генератора g, равный q.
     *
     * @param groupParameters параметры подгруппы
     * @throws IllegalArgumentException если числа или генератор не удовлетворяют условиям
     */
    public static void validateSubgroupParameters(GroupParameters groupParameters) {
        validatePrimeNumbers(groupParameters);

        BigInteger fullGroupOrder = groupParameters.primeModulus().subtract(BigInteger.ONE);
        if (!fullGroupOrder.mod(groupParameters.subgroupOrder()).equals(BigInteger.ZERO)) {
            throw new IllegalArgumentException("Порядок q должен делить p − 1 без остатка");
        }

        BigInteger generator = groupParameters.generator();
        validateElementRange(groupParameters, generator, "Генератор g");
        if (!generator.modPow(groupParameters.subgroupOrder(), groupParameters.primeModulus())
                .equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("Для подгруппы требуется g^q mod p = 1");
        }

        System.out.println("Параметры подгруппы успешно проверены.");
    }

    /**
     * Проверяет открытый ключ полной группы, исключая значения 1 и p − 1.
     *
     * <p>Предполагает, что параметры полной группы уже прошли проверку.</p>
     *
     * @param groupParameters проверенные параметры полной группы
     * @param publicKey       проверяемый открытый ключ участника
     * @throws IllegalArgumentException если ключ вне диапазона или равен p − 1
     */
    public static void validateFullGroupPublicKey(
            GroupParameters groupParameters,
            BigInteger publicKey
    ) {
        validateElementRange(groupParameters, publicKey, "Открытый ключ");
        if (publicKey.equals(groupParameters.primeModulus().subtract(BigInteger.ONE))) {
            throw new IllegalArgumentException("Открытый ключ p − 1 имеет слишком малый порядок 2");
        }

        System.out.println("Открытый ключ полной группы успешно проверен: " + publicKey);
    }

    /**
     * Проверяет, что открытый ключ принадлежит подгруппе порядка q и отличается от 1.
     *
     * <p>Предполагает, что параметры подгруппы уже прошли проверку.</p>
     *
     * @param groupParameters проверенные параметры подгруппы
     * @param publicKey       проверяемый открытый ключ участника
     * @throws IllegalArgumentException если диапазон или принадлежность подгруппе нарушены
     */
    public static void validateSubgroupPublicKey(
            GroupParameters groupParameters,
            BigInteger publicKey
    ) {
        validateElementRange(groupParameters, publicKey, "Открытый ключ");
        if (!publicKey.modPow(groupParameters.subgroupOrder(), groupParameters.primeModulus())
                .equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("Открытый ключ не принадлежит подгруппе порядка q");
        }

        System.out.println("Открытый ключ подгруппы успешно проверен: " + publicKey);
    }

    /**
     * Проверяет диапазон секретного ключа и его соответствие открытому ключу полной группы.
     *
     * <p>Предполагает, что параметры полной группы уже прошли проверку.</p>
     *
     * @param groupParameters проверенные параметры полной группы
     * @param participantKeyPair проверяемая пара ключей участника
     * @throws IllegalArgumentException если секрет вне диапазона 1…p − 2,
     *                                  открытый ключ неверен или не соответствует секретному
     */
    public static void validateFullGroupKeyPair(
            GroupParameters groupParameters,
            ParticipantKeyPair participantKeyPair
    ) {
        BigInteger privateKey = participantKeyPair.privateKey();
        BigInteger fullGroupOrder = groupParameters.primeModulus().subtract(BigInteger.ONE);
        if (privateKey.signum() <= 0 || privateKey.compareTo(fullGroupOrder) >= 0) {
            throw new IllegalArgumentException("Секретный ключ должен находиться в диапазоне 1…p − 2");
        }

        BigInteger expectedPublicKey = groupParameters.generator()
                .modPow(privateKey, groupParameters.primeModulus());
        if (!expectedPublicKey.equals(participantKeyPair.publicKey())) {
            throw new IllegalArgumentException("Открытый ключ не соответствует секретному ключу");
        }

        validateFullGroupPublicKey(groupParameters, participantKeyPair.publicKey());
        System.out.println("Пара ключей полной группы успешно проверена.");
    }

    /**
     * Проверяет диапазон секретного ключа и его соответствие открытому ключу подгруппы.
     *
     * <p>Предполагает, что параметры подгруппы уже прошли проверку.</p>
     *
     * @param groupParameters проверенные параметры подгруппы
     * @param participantKeyPair проверяемая пара ключей участника
     * @throws IllegalArgumentException если секрет вне диапазона 1 ≤ ключ < q,
     *                                  открытый ключ неверен или не соответствует секретному
     */
    public static void validateSubgroupKeyPair(
            GroupParameters groupParameters,
            ParticipantKeyPair participantKeyPair
    ) {
        BigInteger privateKey = participantKeyPair.privateKey();
        if (privateKey.signum() <= 0
                || privateKey.compareTo(groupParameters.subgroupOrder()) >= 0) {
            throw new IllegalArgumentException("Секретный ключ должен находиться в диапазоне 1 ≤ ключ < q");
        }

        BigInteger expectedPublicKey = groupParameters.generator()
                .modPow(privateKey, groupParameters.primeModulus());
        if (!expectedPublicKey.equals(participantKeyPair.publicKey())) {
            throw new IllegalArgumentException("Открытый ключ не соответствует секретному ключу");
        }

        validateSubgroupPublicKey(groupParameters, participantKeyPair.publicKey());
        System.out.println("Пара ключей подгруппы успешно проверена.");
    }

    /**
     * Проверяет положительные диапазоны и вероятную простоту чисел p и q.
     *
     * @param groupParameters параметры с проверяемыми числами
     * @throws IllegalArgumentException если p не больше 2, q меньше 2 или числа составные
     */
    private static void validatePrimeNumbers(GroupParameters groupParameters) {
        if (groupParameters.primeModulus().compareTo(BigInteger.TWO) <= 0
                || !groupParameters.primeModulus().isProbablePrime(PRIMALITY_TEST_CERTAINTY)) {
            throw new IllegalArgumentException("Модуль p должен быть простым числом больше 2");
        }
        if (groupParameters.subgroupOrder().compareTo(BigInteger.TWO) < 0
                || !groupParameters.subgroupOrder().isProbablePrime(PRIMALITY_TEST_CERTAINTY)) {
            throw new IllegalArgumentException("Порядок q должен быть простым числом не меньше 2");
        }
    }

    /**
     * Проверяет, что элемент лежит между 1 и p, исключая обе границы.
     *
     * @param groupParameters параметры, содержащие модуль p
     * @param groupElement    проверяемый элемент группы
     * @param elementName     название элемента для сообщения об ошибке
     * @throws IllegalArgumentException если элемент вне допустимого диапазона
     */
    private static void validateElementRange(
            GroupParameters groupParameters,
            BigInteger groupElement,
            String elementName
    ) {
        if (groupElement.compareTo(BigInteger.ONE) <= 0
                || groupElement.compareTo(groupParameters.primeModulus()) >= 0) {
            throw new IllegalArgumentException(elementName + " должен находиться в диапазоне 1 < значение < p");
        }
    }
}
