package securetransfer.common.protocol;

/**
 * Алгоритмы обмена ключами, доступные в меню сервера и сетевых сообщениях.
 */
public enum ProtocolType {
    /**
     * Диффи — Хеллман в полной мультипликативной группе.
     */
    DH_FULL_GROUP("DH в полной группе"),

    /**
     * Диффи — Хеллман в подгруппе простого порядка.
     */
    DH_SUBGROUP("DH в подгруппе"),

    /**
     * MQV с долговременными и временными ключами.
     */
    MQV("MQV");

    /**
     * Название алгоритма для вывода в консоль.
     */
    private final String displayName;

    /**
     * Задаёт понятное название алгоритма.
     *
     * @param displayName название для меню и сообщений
     */
    ProtocolType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Возвращает название для пользователя.
     *
     * @return название алгоритма
     */
    public String getDisplayName() {
        return displayName;
    }
}
