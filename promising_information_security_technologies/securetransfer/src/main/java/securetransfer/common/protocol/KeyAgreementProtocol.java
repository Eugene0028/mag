package securetransfer.common.protocol;

import java.math.BigInteger;

/**
 * Действия одного участника одного обмена ключами.
 *
 * <p>Реализация хранит собственные ключи, подготавливает открытые данные
 * и проверяет данные собеседника перед вычислением общего секрета.
 * Для нового обмена создаются новый объект и новые разовые ключи.</p>
 */
public interface KeyAgreementProtocol {
    /**
     * Подготавливает открытые данные для передачи собеседнику.
     *
     * @return параметры и открытые ключи без секретных значений
     */
    PublicExchangeData getPublicExchangeData();

    /**
     * Проверяет данные собеседника и вычисляет общий секрет.
     *
     * @param otherParticipantData открытые данные собеседника
     * @return общий секрет без финального хэширования
     * @throws IllegalArgumentException если тип данных, параметры или ключи неверны
     * @throws IllegalStateException если алгоритм требует повторить обмен с новыми ключами
     */
    BigInteger calculateSharedSecret(PublicExchangeData otherParticipantData);
}
