package securetransfer.core;

import java.math.BigInteger;

/**
 * Секретный показатель и открытый ключ одного участника обмена.
 *
 * <p>У Алисы это пара a, A, у Боба — b, B.
 * Открытый ключ вычисляется как g^privateKey mod p.</p>
 *
 * @param privateKey секретный показатель, который остаётся у участника
 * @param publicKey       открытый ключ, который передаётся другому участнику
 */
public record ParticipantKeyPair(
        BigInteger privateKey,
        BigInteger publicKey
) {
}
