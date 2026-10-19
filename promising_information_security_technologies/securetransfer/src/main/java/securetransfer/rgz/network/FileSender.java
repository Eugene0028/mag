package securetransfer.rgz.network;

import securetransfer.rgz.cipher.StreamCipher;
import securetransfer.rgz.measurement.TransferMeasurement;
import securetransfer.rgz.measurement.TransferPhase;
import securetransfer.rgz.config.RgzSettings;
import securetransfer.rgz.file.FileCipher;
import securetransfer.rgz.file.FileHeader;
import securetransfer.rgz.file.InitializationVectorSequence;

import javax.crypto.Mac;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Отправка одного файла через уже подтверждённый сеанс MQV. */
public final class FileSender {
    /** Постоянный источник IV отправителя. */
    private final InitializationVectorSequence vectorSequence;

    /**
     * Подключает журнал IV без создания сетевых соединений.
     *
     * @param vectorSequence источник неповторяющихся IV
     */
    public FileSender(InitializationVectorSequence vectorSequence) {
        this.vectorSequence = Objects.requireNonNull(vectorSequence);
    }

    /**
     * Передаёт заголовок, шифротекст, HMAC, завершает направление отправки и ждёт ответ.
     *
     * @param sourceFile исходный обычный файл
     * @param connection соединение, в котором подготовлен сеанс
     * @param input сетевой вход для ответа
     * @param output сетевой выход
     * @param session подтверждённые ключи MQV
     * @return проверенное подтверждение приёма
     * @throws IOException если файл изменился по размеру, передача прервалась или приём не подтверждён
     */
    public String send(Path sourceFile, Socket connection, DataInputStream input,
                       DataOutputStream output, FileTransferSession session) throws IOException {
        return send(sourceFile, connection, input, output, session, new TransferMeasurement());
    }

    /**
     * Передаёт файл с раздельным учётом прошедшего времени фаз.
     * @param sourceFile исходный файл
     * @param connection TCP-соединение
     * @param input вход подтверждения
     * @param output выход шифротекста
     * @param session подтверждённый сеанс
     * @param measurement замеры текущего отправителя
     * @return проверенное сообщение получателя
     * @throws IOException если передача не подтверждена
     */
    public String send(Path sourceFile, Socket connection, DataInputStream input,
                       DataOutputStream output, FileTransferSession session,
                       TransferMeasurement measurement) throws IOException {
        long transferStarted = System.nanoTime();
        if (!Files.isRegularFile(sourceFile)) {
            throw new IOException("Исходный путь не является обычным файлом: " + sourceFile);
        }
        long fileSize = Files.size(sourceFile);
        measurement.identifyFile(sourceFile.getFileName().toString(), fileSize);
        int vectorLength = RgzSettings.FILE_CIPHER.createCipher().getInitializationVectorLengthBytes();
        FileHeader header = new FileHeader(FileHeader.CURRENT_VERSION, sourceFile.getFileName().toString(),
                fileSize, RgzSettings.FILE_CIPHER, vectorSequence.reserveNext(vectorLength));
        long setupStarted = System.nanoTime();
        StreamCipher cipher = session.createFileCipher(header);
        measurement.finishInterval(TransferPhase.CIPHER_SETUP, setupStarted);
        Mac fileMac = session.createFileAuthenticationMac();
        long headerStarted = System.nanoTime();
        FileTransferProtocol.sendHeader(output, header, fileMac);
        measurement.finishInterval(TransferPhase.NETWORK_IO, headerStarted);
        try (InputStream source = Files.newInputStream(sourceFile)) {
            FileCipher.encrypt(source, output, fileSize, cipher, fileMac, measurement);
            if (source.read() != -1) {
                throw new IOException("Размер исходного файла увеличился во время чтения; передача отменена");
            }
        }
        byte[] authenticationTag = fileMac.doFinal();
        long completionStarted = System.nanoTime();
        output.write(authenticationTag);
        output.flush();
        connection.shutdownOutput();

        FileTransferProtocol.Acknowledgement acknowledgement;
        try {
            acknowledgement = FileTransferProtocol.receiveAcknowledgement(input, session);
        } catch (IOException acknowledgementFailure) {
            throw new IOException("Не получено достоверное подтверждение приёма. "
                    + "Файл мог быть сохранён получателем: " + acknowledgementFailure.getMessage(), acknowledgementFailure);
        }
        measurement.finishInterval(TransferPhase.NETWORK_IO, completionStarted);
        if (!acknowledgement.accepted()) {
            throw new IOException("Получатель отклонил файл: " + acknowledgement.message());
        }
        measurement.finishInterval(TransferPhase.TRANSFER_ELAPSED, transferStarted);
        return acknowledgement.message();
    }
}
