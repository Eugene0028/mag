package securetransfer.rgz.file;

import securetransfer.rgz.cipher.StreamCipher;
import securetransfer.rgz.config.RgzSettings;
import securetransfer.rgz.measurement.TransferMeasurement;
import securetransfer.rgz.measurement.TransferPhase;

import javax.crypto.Mac;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Обработка файла фиксированным буфером с одним состоянием шифра на весь файл. */
public final class FileCipher {
    /** Запрещает создание экземпляров класса потоковой обработки. */
    private FileCipher() {
    }

    /**
     * Шифрует ровно указанное количество байтов и добавляет шифротекст в HMAC.
     *
     * @param input исходный файл
     * @param output получатель шифротекста
     * @param byteCount ожидаемый размер файла
     * @param cipher уже инициализированный шифр
     * @param authenticationMac HMAC с уже добавленным заголовком
     * @throws IOException если файл оборвался или ввод-вывод не удался
     */
    public static void encrypt(InputStream input, OutputStream output, long byteCount,
                               StreamCipher cipher, Mac authenticationMac) throws IOException {
        encrypt(input, output, byteCount, cipher, authenticationMac, new TransferMeasurement());
    }

    /**
     * Расшифровывает ровно указанное количество байтов, добавляя исходный шифротекст в HMAC.
     * Результат следует публиковать только после проверки итогового тега.
     *
     * @param input поток шифротекста
     * @param output временный файл
     * @param byteCount ожидаемый размер шифротекста
     * @param cipher уже инициализированный шифр
     * @param authenticationMac HMAC с уже добавленным заголовком
     * @throws IOException если передача оборвалась или ввод-вывод не удался
     */
    public static void decrypt(InputStream input, OutputStream output, long byteCount,
                               StreamCipher cipher, Mac authenticationMac) throws IOException {
        decrypt(input, output, byteCount, cipher, authenticationMac, new TransferMeasurement());
    }

    /**
     * Шифрует файл и отдельно измеряет шифр, HMAC, диск и сетевой вывод.
     * @param input исходный файл
     * @param output сетевой поток
     * @param byteCount размер файла
     * @param cipher состояние шифра
     * @param authenticationMac состояние HMAC
     * @param measurement накопитель времени текущего соединения
     * @throws IOException при ошибке чтения или записи
     */
    public static void encrypt(InputStream input, OutputStream output, long byteCount,
                               StreamCipher cipher, Mac authenticationMac,
                               TransferMeasurement measurement) throws IOException {
        process(input, output, byteCount, cipher, authenticationMac, true, measurement);
    }

    /**
     * Расшифровывает файл и отдельно измеряет шифр, HMAC, сетевой ввод и диск.
     * @param input сетевой поток
     * @param output временный файл
     * @param byteCount размер файла
     * @param cipher состояние шифра
     * @param authenticationMac состояние HMAC
     * @param measurement накопитель времени текущего соединения
     * @throws IOException при ошибке чтения или записи
     */
    public static void decrypt(InputStream input, OutputStream output, long byteCount,
                               StreamCipher cipher, Mac authenticationMac,
                               TransferMeasurement measurement) throws IOException {
        process(input, output, byteCount, cipher, authenticationMac, false, measurement);
    }

    /**
     * Продолжает состояние шифра между порциями и учитывает в MAC только шифротекст.
     *
     * @param input исходный поток
     * @param output поток результата
     * @param byteCount точное количество обрабатываемых байтов
     * @param cipher состояние шифра текущего файла
     * @param authenticationMac состояние проверки целостности
     * @param encrypting true для шифрования, false для расшифрования
     * @param measurement замеры операций без смешивания с ожиданием ввода-вывода
     * @throws IOException если поток короче заявленного размера или запись не удалась
     */
    private static void process(InputStream input, OutputStream output, long byteCount,
                                StreamCipher cipher, Mac authenticationMac, boolean encrypting,
                                TransferMeasurement measurement) throws IOException {
        if (byteCount < 0) {
            throw new IllegalArgumentException("Количество байтов не может быть отрицательным");
        }
        byte[] inputBuffer = new byte[RgzSettings.FILE_BUFFER_LENGTH_BYTES];
        byte[] outputBuffer = new byte[inputBuffer.length];
        long remainingBytes = byteCount;
        while (remainingBytes > 0) {
            long inputStarted = System.nanoTime();
            int readCount = input.read(inputBuffer, 0, (int) Math.min(inputBuffer.length, remainingBytes));
            measurement.finishInterval(encrypting ? TransferPhase.DISK_IO : TransferPhase.NETWORK_IO, inputStarted);
            if (readCount < 0) {
                throw new EOFException("Поток оборвался; не получено байтов: " + remainingBytes);
            }
            if (readCount == 0) {
                continue;
            }
            if (!encrypting) {
                long authenticationStarted = System.nanoTime();
                authenticationMac.update(inputBuffer, 0, readCount);
                measurement.finishInterval(TransferPhase.AUTHENTICATION, authenticationStarted);
            }
            long cipherStarted = System.nanoTime();
            cipher.processBytes(inputBuffer, 0, readCount, outputBuffer, 0);
            measurement.finishInterval(TransferPhase.CIPHER, cipherStarted);
            if (encrypting) {
                long authenticationStarted = System.nanoTime();
                authenticationMac.update(outputBuffer, 0, readCount);
                measurement.finishInterval(TransferPhase.AUTHENTICATION, authenticationStarted);
            }
            long outputStarted = System.nanoTime();
            output.write(outputBuffer, 0, readCount);
            measurement.finishInterval(encrypting ? TransferPhase.NETWORK_IO : TransferPhase.DISK_IO, outputStarted);
            remainingBytes -= readCount;
        }
    }
}
