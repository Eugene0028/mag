package securetransfer.core;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Генерация общих открытых параметров группы для обмена ключами.
 */
public final class GroupParameterGenerator {
    /**
     * Источник случайных чисел для генерации параметров группы.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Параметр достоверности вероятностных проверок простоты.
     */
    private static final int PRIMALITY_TEST_CERTAINTY = 128;

    /**
     * Служебный конструктор класса статических операций.
     */
    private GroupParameterGenerator() {
    }

    /**
     * Генерирует параметры полной мультипликативной группы порядка p − 1 = 2q.
     *
     * <p>Подбирает вероятно простые q и p = 2q + 1. Затем перебирает значения g,
     * пока не выполнены оба условия: g^2 mod p != 1 и g^q mod p != 1.
     * Эти условия исключают порядки 1, 2 и q, оставляя порядок 2q.</p>
     *
     * @param modulusBitLength требуемая битовая длина числа p, не меньше 3
     * @return параметры p, q и генератор g полной группы
     * @throws IllegalArgumentException если битовая длина меньше 3
     */
    public static GroupParameters generateFullGroupParameters(int modulusBitLength) {
        if (modulusBitLength < 3) {
            throw new IllegalArgumentException("Битовая длина p должна быть не меньше 3");
        }

        BigInteger subgroupOrder; // q
        BigInteger primeModulus; // p

        // Выбираем простое q и повторяем попытку, пока p = 2q + 1 не станет простым.
        do {
            subgroupOrder = new BigInteger(
                    modulusBitLength - 1,
                    PRIMALITY_TEST_CERTAINTY,
                    SECURE_RANDOM
            );
            primeModulus = subgroupOrder.multiply(BigInteger.TWO).add(BigInteger.ONE);
        } while (!primeModulus.isProbablePrime(PRIMALITY_TEST_CERTAINTY));

        // Ищем элемент, порядок которого равен полному порядку группы 2q.
        BigInteger generator = BigInteger.TWO;
        while (generator.modPow(BigInteger.TWO, primeModulus).equals(BigInteger.ONE)
                || generator.modPow(subgroupOrder, primeModulus).equals(BigInteger.ONE)) {
            generator = generator.add(BigInteger.ONE);
        }

        return new GroupParameters(primeModulus, subgroupOrder, generator);
    }

    /**
     * Генерирует параметры подгруппы простого порядка q.
     *
     * <p>Выбирает вероятно простое q и случайный чётный множитель t,
     * пока p = tq + 1 не пройдёт проверку простоты и не получит нужную битовую длину.</p>
     *
     * <p>Затем перебирает основания r, начиная с 2, и вычисляет g = r^t mod p.
     * Если g != 1, его порядок равен простому числу q.</p>
     *
     * @param modulusBitLength       требуемая битовая длина числа p
     * @param subgroupOrderBitLength битовая длина q, от 2 до modulusBitLength − 1
     * @return параметры p, q и генератор g подгруппы порядка q
     * @throws IllegalArgumentException если битовая длина q меньше 2 или не меньше длины p
     */
    public static GroupParameters generateSubgroupParameters(
            int modulusBitLength,
            int subgroupOrderBitLength
    ) {
        if (subgroupOrderBitLength < 2 || subgroupOrderBitLength >= modulusBitLength) {
            throw new IllegalArgumentException("Битовая длина q должна быть от 2 до длины p − 1");
        }

        BigInteger subgroupOrder; // q
        BigInteger primeModulus; // p
        BigInteger subgroupCofactor; // t = (p − 1) / q

        do {
            subgroupOrder = new BigInteger(
                    subgroupOrderBitLength,
                    PRIMALITY_TEST_CERTAINTY,
                    SECURE_RANDOM
            );

            // Умножение случайного числа на 2 даёт чётный множитель t.
            subgroupCofactor = new BigInteger(
                    modulusBitLength - subgroupOrderBitLength,
                    SECURE_RANDOM
            ).multiply(BigInteger.TWO);

            primeModulus = subgroupCofactor.multiply(subgroupOrder).add(BigInteger.ONE);
        } while (primeModulus.bitLength() != modulusBitLength
                || !primeModulus.isProbablePrime(PRIMALITY_TEST_CERTAINTY));

        BigInteger generatorBase = BigInteger.TWO; // r
        BigInteger generator = generatorBase.modPow(subgroupCofactor, primeModulus);
        while (generator.equals(BigInteger.ONE)) {
            generatorBase = generatorBase.add(BigInteger.ONE);
            generator = generatorBase.modPow(subgroupCofactor, primeModulus);
        }

        return new GroupParameters(primeModulus, subgroupOrder, generator);
    }
}
