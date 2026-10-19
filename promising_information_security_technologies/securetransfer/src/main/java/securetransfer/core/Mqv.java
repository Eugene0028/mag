package securetransfer.core;

import java.math.BigInteger;

/**
 * Вычисление общего секрета MQV в подгруппе простого порядка q.
 *
 * <p>Объединяет долговременный и временный ключи участника.
 * Возвращает число без финального хэширования.</p>
 */
public final class Mqv {
    /**
     * Служебный конструктор класса статических вычислений.
     */
    private Mqv() {
    }

    /**
     * Вычисляет общий секрет из собственных секретных ключей и открытых ключей собеседника.
     *
     * <p>Для Алисы: d вычисляется из X, e — из Y, затем s = (x + d·a) mod q
     * и S = (Y·B^e)^s mod p. Боб выполняет те же действия со своей стороны.</p>
     *
     * <p>Вызывающий код предварительно проверяет параметры, собственные пары и чужие
     * открытые ключи. Долговременный открытый ключ собеседника берётся из проверенного
     * подписанного справочника.</p>
     *
     * @param subgroupParameters проверенные параметры подгруппы
     * @param ownLongTermPrivateKey собственный долговременный секретный ключ: a у Алисы, b у Боба
     * @param ownEphemeralKeyPair собственная временная пара: x, X у Алисы или y, Y у Боба
     * @param otherParticipantLongTermPublicKey подтверждённый долговременный ключ собеседника
     * @param otherParticipantEphemeralPublicKey проверенный временный открытый ключ собеседника
     * @return общий секрет в виде числа без хэширования
     * @throws IllegalStateException если получен недопустимый общий секрет;
     *                               обмен следует повторить с новыми временными парами
     */
    public static BigInteger calculateSharedSecret(
            GroupParameters subgroupParameters,
            BigInteger ownLongTermPrivateKey,
            ParticipantKeyPair ownEphemeralKeyPair,
            BigInteger otherParticipantLongTermPublicKey,
            BigInteger otherParticipantEphemeralPublicKey
    ) {
        BigInteger primeModulus = subgroupParameters.primeModulus();
        BigInteger subgroupOrder = subgroupParameters.subgroupOrder();

        BigInteger ownPublicKeyCoefficient = calculateEphemeralPublicKeyCoefficient(
                subgroupOrder,
                ownEphemeralKeyPair.publicKey()
        );
        BigInteger otherParticipantPublicKeyCoefficient = calculateEphemeralPublicKeyCoefficient(
                subgroupOrder,
                otherParticipantEphemeralPublicKey
        );

        // Для Алисы: s = (x + d·a) mod q.
        BigInteger combinedPrivateKey = ownEphemeralKeyPair.privateKey()
                .add(ownPublicKeyCoefficient.multiply(ownLongTermPrivateKey))
                .mod(subgroupOrder);

        // Для Алисы: Y·B^e mod p.
        BigInteger combinedOtherParticipantPublicKey = otherParticipantEphemeralPublicKey
                .multiply(otherParticipantLongTermPublicKey.modPow(
                        otherParticipantPublicKeyCoefficient,
                        primeModulus
                ))
                .mod(primeModulus);

        BigInteger sharedSecret = combinedOtherParticipantPublicKey.modPow(combinedPrivateKey, primeModulus);
        if (sharedSecret.compareTo(BigInteger.ONE) <= 0
                || sharedSecret.equals(primeModulus.subtract(BigInteger.ONE))) {
            throw new IllegalStateException(
                    "Получен недопустимый общий секрет MQV. Повторите обмен с новыми временными парами."
            );
        }

        return sharedSecret;
    }

    /**
     * Вычисляет коэффициент 2^l + (открытый ключ mod 2^l), используемый в формулах MQV.
     *
     * <p>l — половина битовой длины q с округлением вверх.
     * Для временного ключа X получается d, для Y — e.</p>
     *
     * @param subgroupOrder простой порядок подгруппы q
     * @param ephemeralPublicKey временный открытый ключ X или Y
     * @return коэффициент d или e
     */
    private static BigInteger calculateEphemeralPublicKeyCoefficient(
            BigInteger subgroupOrder,
            BigInteger ephemeralPublicKey
    ) {
        int coefficientBitLength = (subgroupOrder.bitLength() + 1) / 2;
        BigInteger coefficientBase = BigInteger.ONE.shiftLeft(coefficientBitLength);
        return coefficientBase.add(ephemeralPublicKey.mod(coefficientBase));
    }
}
