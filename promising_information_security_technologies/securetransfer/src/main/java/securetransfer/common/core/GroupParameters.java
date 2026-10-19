package securetransfer.common.core;

import java.math.BigInteger;

/**
 * Общие открытые параметры группы для обмена ключами.
 *
 * <p>Оба участника используют одинаковые значения p, q и g.
 * Арифметические операции выполняются по модулю p.</p>
 *
 * @param primeModulus  простое число p — модуль вычислений
 * @param subgroupOrder простое число q — порядок подгруппы;
 *                      порядок полной мультипликативной группы равен p − 1
 * @param generator     число g — генератор выбранной группы
 */
public record GroupParameters(
        BigInteger primeModulus,
        BigInteger subgroupOrder,
        BigInteger generator
) {
}
