package securetransfer.protocol;

import securetransfer.core.GroupParameters;

/**
 * Открытые данные участника, передаваемые при обмене ключами.
 *
 * <p>DH передаёт один открытый ключ, MQV — долговременный и временный.</p>
 */
public interface PublicExchangeData {
    /**
     * Возвращает параметры группы, используемые отправителем.
     *
     * @return открытые параметры p, q, g
     */
    GroupParameters groupParameters();
}
