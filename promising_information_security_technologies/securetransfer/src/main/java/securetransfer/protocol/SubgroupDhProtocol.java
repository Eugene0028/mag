package securetransfer.protocol;

import securetransfer.core.DiffieHellman;
import securetransfer.core.GroupParameters;
import securetransfer.core.GroupValidator;
import securetransfer.core.ParticipantKeyPair;

import java.math.BigInteger;

/**
 * Участник обмена Диффи — Хеллмана в подгруппе простого порядка q.
 */
public final class SubgroupDhProtocol implements KeyAgreementProtocol {
    /**
     * Предварительно проверенные общие параметры подгруппы.
     */
    private final GroupParameters subgroupParameters;

    /**
     * Собственная пара ключей текущего обмена.
     */
    private final ParticipantKeyPair ownKeyPair;

    /**
     * Проверяет собственную пару и подготавливает участника обмена.
     *
     * @param subgroupParameters предварительно проверенные параметры подгруппы
     * @param ownKeyPair собственная пара, созданная для текущего обмена
     * @throws IllegalArgumentException если собственная пара ключей неверна
     */
    public SubgroupDhProtocol(GroupParameters subgroupParameters, ParticipantKeyPair ownKeyPair) {
        GroupValidator.validateSubgroupKeyPair(subgroupParameters, ownKeyPair);
        this.subgroupParameters = subgroupParameters;
        this.ownKeyPair = ownKeyPair;
    }

    /**
     * Возвращает параметры подгруппы и собственный открытый ключ.
     *
     * @return открытые данные DH
     */
    @Override
    public DhPublicExchangeData getPublicExchangeData() {
        return new DhPublicExchangeData(subgroupParameters, ownKeyPair.publicKey());
    }

    /**
     * Проверяет параметры и открытый ключ собеседника, затем вычисляет общий секрет DH.
     *
     * @param otherParticipantData открытые данные собеседника
     * @return общий секрет без хэширования
     * @throws IllegalArgumentException если данные не относятся к DH, параметры отличаются или ключ неверен
     */
    @Override
    public BigInteger calculateSharedSecret(PublicExchangeData otherParticipantData) {
        if (!(otherParticipantData instanceof DhPublicExchangeData dhData)) {
            throw new IllegalArgumentException("Для DH ожидаются параметры и один открытый ключ");
        }
        if (!subgroupParameters.equals(dhData.groupParameters())) {
            throw new IllegalArgumentException("Параметры подгруппы собеседника отличаются от собственных");
        }
        GroupValidator.validateSubgroupPublicKey(subgroupParameters, dhData.publicKey());
        return DiffieHellman.calculateSharedSecret(subgroupParameters, ownKeyPair.privateKey(), dhData.publicKey());
    }
}
