package securetransfer.rgz;

import securetransfer.common.console.ConsoleInput;
import securetransfer.common.protocol.KeyAgreementProtocol;
import securetransfer.common.protocol.KeyAgreementProtocolFactory;
import securetransfer.common.protocol.ProtocolType;
import securetransfer.rgz.config.RgzSettings;
import securetransfer.rgz.file.FileChecksum;
import securetransfer.rgz.measurement.TransferMeasurement;
import securetransfer.rgz.measurement.TransferPhase;
import securetransfer.rgz.measurement.TransferReportWriter;
import securetransfer.rgz.network.FileReceiver;
import securetransfer.rgz.network.FileTransferSession;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Сервер приёма файлов РГЗ; одно соединение и новый MQV-сеанс на каждый файл. */
public final class FileReceiverMain {
    /** Запрещает создание экземпляров точки входа. */
    private FileReceiverMain() {
    }

    /**
     * Спрашивает каталог и порт, подготавливает доверие MQV и последовательно принимает файлы.
     * Остановка выполняется кнопкой Stop в IDEA или Ctrl+C.
     *
     * @param commandLineArguments не используются; параметры вводятся в консоли
     */
    public static void main(String[] commandLineArguments) {
        try {
            String directoryText = ConsoleInput.readLine("Каталог приёма [" + RgzSettings.RECEIVED_FILES_DIRECTORY + "]: ");
            Path receivingDirectory = directoryText.isBlank()
                    ? RgzSettings.RECEIVED_FILES_DIRECTORY : Path.of(directoryText);
            String portText = ConsoleInput.readLine("Порт [" + RgzSettings.FILE_TRANSFER_PORT + "]: ");
            int port = portText.isBlank() ? RgzSettings.FILE_TRANSFER_PORT : Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Порт должен находиться в диапазоне 1…65535");
            }
            KeyAgreementProtocolFactory factory = new KeyAgreementProtocolFactory(
                    RgzSettings.KEY_AGREEMENT_GROUP_SETTINGS, RgzSettings.DATA_PATHS
            );
            TransferReportWriter report = new TransferReportWriter("Ручной приём; адреса собеседников записаны в CSV.");
            System.out.println("Замеры: " + report.reportDirectory());
            try (ServerSocket listener = new ServerSocket(); FileReceiver receiver = new FileReceiver(receivingDirectory)) {
                listener.setReuseAddress(true);
                listener.bind(new InetSocketAddress(port));
                System.out.println("Получатель: Alice; шифр: " + RgzSettings.FILE_CIPHER.createCipher().getName());
                System.out.println("Каталог: " + receivingDirectory.toAbsolutePath().normalize());
                while (!listener.isClosed()) {
                    TransferMeasurement measurement = new TransferMeasurement();
                    long preparationStarted = System.nanoTime();
                    KeyAgreementProtocol receiverProtocol = factory.createServerProtocol(ProtocolType.MQV);
                    measurement.finishInterval(TransferPhase.PREPARATION, preparationStarted);
                    System.out.println("Получатель ожидает подключение на порту " + port);
                    Socket connection = listener.accept();
                    receiveConnection(connection, receiverProtocol, receiver, measurement, report);
                }
            }
        } catch (BindException occupiedPort) {
            System.err.println("Порт занят. Остановите прежний получатель или выберите другой порт при запуске.");
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException failure) {
            System.err.println("Получатель остановлен: " + failure.getMessage());
        }
    }

    /**
     * Ограничивает ожидание чтения, подготавливает новый сеанс и принимает один файл.
     * Ошибка одного соединения не завершает сервер.
     *
     * @param connection принятое TCP-соединение
     * @param receiverProtocol новый экземпляр MQV
     * @param receiver владелец каталога приёма
     * @param measurement накопитель времени без ожидания accept
     * @param report запись CSV и HTML после успешного приёма
     */
    private static void receiveConnection(
            Socket connection, KeyAgreementProtocol receiverProtocol, FileReceiver receiver,
            TransferMeasurement measurement, TransferReportWriter report
    ) {
        try (connection;
             DataInputStream input = new DataInputStream(new BufferedInputStream(connection.getInputStream(),
                     RgzSettings.FILE_BUFFER_LENGTH_BYTES));
             DataOutputStream output = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream(),
                     RgzSettings.FILE_BUFFER_LENGTH_BYTES))) {
            connection.setSoTimeout(RgzSettings.READ_TIMEOUT_MILLISECONDS);
            System.out.println("Подключился отправитель: " + connection.getRemoteSocketAddress());
            long keyStarted = System.nanoTime();
            try (FileTransferSession session = FileTransferSession.establishReceiver(input, output, receiverProtocol)) {
                measurement.finishInterval(TransferPhase.KEY_ESTABLISHMENT, keyStarted);
                System.out.println("Сеанс MQV подтверждён. Приём файла.");
                Path receivedFile = receiver.receive(input, output, session, measurement);
                Matcher batchName = Pattern.compile(".*-(warmup|file)-[0-9]+-([0-9]+)\\.bin")
                        .matcher(measurement.fileName());
                boolean batchFile = batchName.matches();
                boolean warmup = batchFile && batchName.group(1).equals("warmup");
                int repetition = batchFile ? Integer.parseInt(batchName.group(2)) : 1;
                report.append(measurement, "receiver", connection.getRemoteSocketAddress().toString(),
                        repetition, warmup, "HMAC verified; received SHA-256=" + FileChecksum.sha256(receivedFile));
                System.out.println("Файл принят и проверен: " + receivedFile);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException receptionFailure) {
            String failureMessage = receptionFailure.getMessage() == null
                    ? receptionFailure.getClass().getSimpleName() : receptionFailure.getMessage();
            System.err.println("Соединение завершено: " + failureMessage);
            for (Throwable cleanupFailure : receptionFailure.getSuppressed()) {
                System.err.println("Дополнительная ошибка: " + cleanupFailure.getMessage());
            }
        }
    }
}
