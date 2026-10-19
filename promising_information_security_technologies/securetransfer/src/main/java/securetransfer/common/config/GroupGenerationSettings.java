package securetransfer.common.config;

/**
 * Размеры чисел для генерации параметров полной группы и подгруппы.
 *
 * @param modulusBitLength битовая длина простого модуля p
 * @param subgroupOrderBitLength битовая длина простого порядка подгруппы q
 */
public record GroupGenerationSettings(int modulusBitLength, int subgroupOrderBitLength) {
    /** Проверяет допустимость размеров до генерации или чтения параметров. */
    public GroupGenerationSettings {
        if (modulusBitLength < 4 || subgroupOrderBitLength < 2
                || subgroupOrderBitLength >= modulusBitLength) {
            throw new IllegalArgumentException("Требуются p >= 4 бит и 2 <= длина q < длины p.");
        }
    }
}
