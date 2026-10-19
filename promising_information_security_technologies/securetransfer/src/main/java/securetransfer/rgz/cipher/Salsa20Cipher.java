package securetransfer.rgz.cipher;

import org.bouncycastle.crypto.engines.Salsa20Engine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/** Salsa20 с 20 раундами и ключом 128 бит через Bouncy Castle. */
public final class Salsa20Cipher implements StreamCipher {
    /** Число раундов выбранного варианта Salsa20. */
    private static final int ROUND_COUNT = 20;

    /** Состояние потока; отсутствует до инициализации. */
    private Salsa20Engine cipherEngine;

    /** @return название с указанием числа раундов */
    @Override
    public String getName() {
        return "Salsa20/20";
    }

    /** @return длина выбранного ключа: 16 байт */
    @Override
    public int getKeyLengthBytes() {
        return 16;
    }

    /** @return длина IV: 8 байт */
    @Override
    public int getInitializationVectorLengthBytes() {
        return 8;
    }

    /** {@inheritDoc} */
    @Override
    public void initialize(byte[] secretKey, byte[] initializationVector) {
        CipherArguments.validateKey(this, secretKey);
        CipherArguments.validateInitializationVector(this, initializationVector);
        Salsa20Engine initializedEngine = new Salsa20Engine(ROUND_COUNT);
        ParametersWithIV cipherParameters = new ParametersWithIV(new KeyParameter(secretKey), initializationVector);
        initializedEngine.init(true, cipherParameters);
        cipherEngine = initializedEngine;
    }

    /** {@inheritDoc} */
    @Override
    public void processBytes(
            byte[] inputBytes, int inputOffset, int byteCount, byte[] outputBytes, int outputOffset
    ) {
        if (cipherEngine == null) {
            throw new IllegalStateException("Перед обработкой данных вызовите initialize для Salsa20");
        }
        CipherArguments.validateBuffers(inputBytes, inputOffset, byteCount, outputBytes, outputOffset);
        cipherEngine.processBytes(inputBytes, inputOffset, byteCount, outputBytes, outputOffset);
    }
}
