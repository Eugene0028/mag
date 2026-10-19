package securetransfer.protocol;

import securetransfer.core.GroupParameters;
import securetransfer.core.GroupValidator;
import securetransfer.core.Mqv;
import securetransfer.core.ParticipantKeyPair;

import java.math.BigInteger;

/**
 * Участник MQV с собственными ключами и заранее подтверждённым долговременным ключом собеседника.
 */
public final class MqvProtocol implements KeyAgreementProtocol {
    /**
     * Предварительно проверенные параметры общей подгруппы.
     */
    private final GroupParameters subgroupParameters;

    /**
     * Собственная долговременная пара ключей.
     */
    private final ParticipantKeyPair ownLongTermKeyPair;

    /**
     * Собственная временная пара текущего обмена.
     */
    private final ParticipantKeyPair ownEphemeralKeyPair;

    /**
     * Долговременный открытый ключ ожидаемого собеседника из проверенного подписанного справочника.
     */
    private final BigInteger trustedOtherParticipantLongTermPublicKey;

    /**
     * Проверяет собственные пары и подготавливает участника к обмену с известным собеседником.
     *
     * @param subgroupParameters предварительно проверенные параметры подгруппы
     * @param ownLongTermKeyPair собственная долговременная пара
     * @param ownEphemeralKeyPair собственная временная пара, созданная для текущего обмена
     * @param trustedOtherParticipantLongTermPublicKey ключ ожидаемого собеседника из проверенного справочника
     * @throws IllegalArgumentException если пары или доверенный открытый ключ математически неверны
     */
    public MqvProtocol(
            GroupParameters subgroupParameters,
            ParticipantKeyPair ownLongTermKeyPair,
            ParticipantKeyPair ownEphemeralKeyPair,
            BigInteger trustedOtherParticipantLongTermPublicKey
    ) {
        GroupValidator.validateSubgroupKeyPair(subgroupParameters, ownLongTermKeyPair);
        GroupValidator.validateSubgroupKeyPair(subgroupParameters, ownEphemeralKeyPair);
        GroupValidator.validateSubgroupPublicKey(subgroupParameters, trustedOtherParticipantLongTermPublicKey);
        this.subgroupParameters = subgroupParameters;
        this.ownLongTermKeyPair = ownLongTermKeyPair;
        this.ownEphemeralKeyPair = ownEphemeralKeyPair;
        this.trustedOtherParticipantLongTermPublicKey = trustedOtherParticipantLongTermPublicKey;
    }

    /**
     * Возвращает параметры, долговременный и временный открытые ключи.
     *
     * @return открытые данные MQV
     */
    @Override
    public MqvPublicExchangeData getPublicExchangeData() {
        return new MqvPublicExchangeData(
                subgroupParameters,
                ownLongTermKeyPair.publicKey(),
                ownEphemeralKeyPair.publicKey()
        );
    }

    /**
     * Сверяет долговременный ключ с доверенным, проверяет временный ключ и вычисляет секрет MQV.
     *
     * @param otherParticipantData открытые данные ожидаемого собеседника
     * @return общий секрет без хэширования
     * @throws IllegalArgumentException если тип данных, параметры или ключи не соответствуют ожидаемым
     * @throws IllegalStateException если MQV требует повторить обмен с новыми временными парами
     */
    @Override
    public BigInteger calculateSharedSecret(PublicExchangeData otherParticipantData) {
        if (!(otherParticipantData instanceof MqvPublicExchangeData mqvData)) {
            throw new IllegalArgumentException("Для MQV ожидаются долговременный и временный открытые ключи");
        }
        if (!subgroupParameters.equals(mqvData.groupParameters())) {
            throw new IllegalArgumentException("Параметры подгруппы собеседника отличаются от собственных");
        }
        if (!trustedOtherParticipantLongTermPublicKey.equals(mqvData.longTermPublicKey())) {
            throw new IllegalArgumentException("Долговременный ключ собеседника не совпадает с доверенным справочником");
        }
        GroupValidator.validateSubgroupPublicKey(subgroupParameters, mqvData.ephemeralPublicKey());
        return Mqv.calculateSharedSecret(
                subgroupParameters,
                ownLongTermKeyPair.privateKey(),
                ownEphemeralKeyPair,
                trustedOtherParticipantLongTermPublicKey,
                mqvData.ephemeralPublicKey()
        );
    }
}
