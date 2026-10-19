package securetransfer.network;

import securetransfer.protocol.DhPublicExchangeData;
import securetransfer.protocol.MqvPublicExchangeData;
import securetransfer.protocol.ProtocolType;
import securetransfer.protocol.PublicExchangeData;

import java.util.Objects;

/**
 * Сетевое сообщение с выбранным алгоритмом и открытыми данными участника.
 *
 * @param protocolType выбранный алгоритм
 * @param publicExchangeData параметры группы и открытые ключи
 */
public record KeyExchangeMessage(
        ProtocolType protocolType,
        PublicExchangeData publicExchangeData
) {
    /**
     * Проверяет соответствие состава открытых данных выбранному алгоритму.
     *
     * @param protocolType выбранный алгоритм
     * @param publicExchangeData открытые данные участника
     * @throws NullPointerException если обязательное поле отсутствует
     * @throws IllegalArgumentException если состав данных не соответствует алгоритму
     */
    public KeyExchangeMessage {
        Objects.requireNonNull(protocolType, "Алгоритм обмена отсутствует");
        Objects.requireNonNull(publicExchangeData, "Открытые данные отсутствуют");
        boolean expectedDataType = protocolType == ProtocolType.MQV
                ? publicExchangeData instanceof MqvPublicExchangeData
                : publicExchangeData instanceof DhPublicExchangeData;
        if (!expectedDataType) {
            throw new IllegalArgumentException("Состав открытых данных не соответствует выбранному алгоритму");
        }
    }
}
