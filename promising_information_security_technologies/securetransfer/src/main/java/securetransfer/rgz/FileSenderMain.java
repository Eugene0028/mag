package securetransfer.rgz;

import securetransfer.common.console.ConsoleInput;
import securetransfer.common.protocol.KeyAgreementProtocolFactory;
import securetransfer.rgz.config.RgzSettings;
import securetransfer.rgz.measurement.TransferMeasurement;
import securetransfer.rgz.measurement.TransferPhase;
import securetransfer.rgz.measurement.TransferReportWriter;
import securetransfer.rgz.file.InitializationVectorSequence;
import securetransfer.rgz.network.FileSender;
import securetransfer.rgz.network.FileTransferSession;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

/** Клиент отправки одного файла с MQV, Sosemanuk и проверяемым подтверждением приёма. */
public final class FileSenderMain {
    /** Запрещает создание экземпляров точки входа. */
    private FileSenderMain() {
    }

    /**
     * Спрашивает файл, адрес и порт, подготавливает MQV и отправляет файл через буфер.
     *
     * @param commandLineArguments не используются; параметры вводятся в консоли
     */
    public static void main(String[] commandLineArguments) {
        try {
            String sourceText = ConsoleInput.readLine("Путь к отправляемому файлу: ");
            if (sourceText.isBlank()) {
                throw new IOException("Путь к файлу не указан");
            }
            Path sourceFile = Path.of(sourceText).toAbsolutePath().normalize();
            if (!Files.isRegularFile(sourceFile)) {
                throw new IOException("Не найден обычный файл: " + sourceFile);
            }
            String hostText = ConsoleInput.readLine("Адрес получателя [" + RgzSettings.DEFAULT_RECEIVER_HOST + "]: ");
            String host = hostText.isBlank() ? RgzSettings.DEFAULT_RECEIVER_HOST : hostText;
            String portText = ConsoleInput.readLine("Порт [" + RgzSettings.FILE_TRANSFER_PORT + "]: ");
            int port = portText.isBlank() ? RgzSettings.FILE_TRANSFER_PORT : Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Порт должен находиться в диапазоне 1…65535");
            }
            KeyAgreementProtocolFactory factory = new KeyAgreementProtocolFactory(
                    RgzSettings.KEY_AGREEMENT_GROUP_SETTINGS, RgzSettings.DATA_PATHS
            );
            TransferReportWriter report = new TransferReportWriter("Ручная отправка; адрес получателя: " + host
                    + ". Число физических компьютеров определяется условиями опыта, а не адресом само по себе.");
            TransferMeasurement measurement = new TransferMeasurement();
            try (Socket connection = new Socket()) {
                long connectionStarted = System.nanoTime();
                connection.connect(new InetSocketAddress(host, port), RgzSettings.CONNECT_TIMEOUT_MILLISECONDS);
                measurement.finishInterval(TransferPhase.CONNECTION, connectionStarted);
                connection.setSoTimeout(RgzSettings.READ_TIMEOUT_MILLISECONDS);
                try (DataInputStream input = new DataInputStream(new BufferedInputStream(connection.getInputStream(),
                        RgzSettings.FILE_BUFFER_LENGTH_BYTES));
                     DataOutputStream output = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream(),
                             RgzSettings.FILE_BUFFER_LENGTH_BYTES))) {
                    long keyStarted = System.nanoTime();
                    try (FileTransferSession session = FileTransferSession.establishSender(input, output, factory)) {
                        measurement.finishInterval(TransferPhase.KEY_ESTABLISHMENT, keyStarted);
                        System.out.println("Сеанс MQV подтверждён. Отправка: " + sourceFile.getFileName());
                        FileSender sender = new FileSender(new InitializationVectorSequence(RgzSettings.IV_SEQUENCE_FILE));
                        System.out.println(sender.send(sourceFile, connection, input, output, session, measurement));
                        report.append(measurement, "sender", connection.getRemoteSocketAddress().toString(),
                                1, false, "authenticated ACK");
                        System.out.println("Замеры: " + report.reportDirectory());
                    }
                }
            }
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException failure) {
            String failureMessage = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
            System.err.println("Отправка не подтверждена: " + failureMessage);
        }
    }
}
