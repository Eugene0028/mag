package securetransfer.common.protocol;

import securetransfer.common.core.GroupParameters;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Открытые данные участника MQV.
 *
 * @param groupParameters параметры подгруппы
 * @param longTermPublicKey долговременный открытый ключ; получатель сверяет его с доверенным справочником
 * @param ephemeralPublicKey временный открытый ключ текущего обмена
 */
public record MqvPublicExchangeData(
        GroupParameters groupParameters,
        BigInteger longTermPublicKey,
        BigInteger ephemeralPublicKey
) implements PublicExchangeData {
    /**
     * Проверяет наличие всех полей; доверие к ключам проверяет реализация MQV.
     *
     * @param groupParameters открытые параметры подгруппы
     * @param longTermPublicKey долговременный открытый ключ
     * @param ephemeralPublicKey временный открытый ключ
     * @throws NullPointerException если обязательное поле отсутствует
     */
    public MqvPublicExchangeData {
        Objects.requireNonNull(groupParameters, "Параметры подгруппы отсутствуют");
        Objects.requireNonNull(longTermPublicKey, "Долговременный открытый ключ отсутствует");
        Objects.requireNonNull(ephemeralPublicKey, "Временный открытый ключ отсутствует");
    }
}
