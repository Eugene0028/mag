package securetransfer.protocol;

import securetransfer.core.GroupParameters;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Открытые данные участника Диффи — Хеллмана.
 *
 * @param groupParameters параметры полной группы или подгруппы
 * @param publicKey открытый ключ участника
 */
public record DhPublicExchangeData(
        GroupParameters groupParameters,
        BigInteger publicKey
) implements PublicExchangeData {
    /**
     * Проверяет наличие всех полей; математические проверки выполняет реализация протокола.
     *
     * @param groupParameters открытые параметры группы
     * @param publicKey открытый ключ участника
     * @throws NullPointerException если обязательное поле отсутствует
     */
    public DhPublicExchangeData {
        Objects.requireNonNull(groupParameters, "Параметры группы отсутствуют");
        Objects.requireNonNull(publicKey, "Открытый ключ отсутствует");
    }
}
