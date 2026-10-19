package securetransfer.protocol;

import securetransfer.core.DiffieHellman;
import securetransfer.core.GroupParameters;
import securetransfer.core.GroupValidator;
import securetransfer.core.ParticipantKeyPair;

import java.math.BigInteger;

/**
 * Участник обмена Диффи — Хеллмана в полной мультипликативной группе.
 */
public final class FullGroupDhProtocol implements KeyAgreementProtocol {
    /**
     * Предварительно проверенные общие параметры полной группы.
     */
    private final GroupParameters groupParameters;

    /**
     * Собственная пара ключей текущего обмена.
     */
    private final ParticipantKeyPair ownKeyPair;

    /**
     * Проверяет собственную пару и подготавливает участника обмена.
     *
     * @param groupParameters предварительно проверенные параметры полной группы
     * @param ownKeyPair собственная пара, созданная для текущего обмена
     * @throws IllegalArgumentException если собственная пара ключей неверна
     */
    public FullGroupDhProtocol(GroupParameters groupParameters, ParticipantKeyPair ownKeyPair) {
        GroupValidator.validateFullGroupKeyPair(groupParameters, ownKeyPair);
        this.groupParameters = groupParameters;
        this.ownKeyPair = ownKeyPair;
    }

    /**
     * Возвращает параметры полной группы и собственный открытый ключ.
     *
     * @return открытые данные DH
     */
    @Override
    public DhPublicExchangeData getPublicExchangeData() {
        return new DhPublicExchangeData(groupParameters, ownKeyPair.publicKey());
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
        if (!groupParameters.equals(dhData.groupParameters())) {
            throw new IllegalArgumentException("Параметры группы собеседника отличаются от собственных");
        }
        GroupValidator.validateFullGroupPublicKey(groupParameters, dhData.publicKey());
        return DiffieHellman.calculateSharedSecret(groupParameters, ownKeyPair.privateKey(), dhData.publicKey());
    }
}
