package securetransfer.rgz;

import securetransfer.common.console.ConsoleInput;
import securetransfer.common.protocol.KeyAgreementProtocol;
import securetransfer.common.protocol.KeyAgreementProtocolFactory;
import securetransfer.common.protocol.ProtocolType;
import securetransfer.rgz.config.RgzSettings;
import securetransfer.rgz.file.InitializationVectorSequence;
import securetransfer.rgz.file.FileChecksum;
import securetransfer.rgz.measurement.TransferMeasurement;
import securetransfer.rgz.measurement.TransferPhase;
import securetransfer.rgz.measurement.TransferReportWriter;
import securetransfer.rgz.network.FileReceiver;
import securetransfer.rgz.network.FileSender;
import securetransfer.rgz.network.FileTransferSession;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Воспроизводимая серия через настоящий TCP loopback, два потока одной JVM на одном компьютере. */
public final class TransferExperimentMain {
    /** Запрещает создание экземпляров точки входа. */
    private TransferExperimentMain() {
    }

    /**
     * Проводит прогрев и повторы, проверяет равенство файлов и сохраняет наблюдения обеих сторон.
     * Исходные и принятые искусственные файлы удаляются только после побайтового сравнения.
     * @param commandLineArguments не используются; режим вводится в консоли, размеры заданы в RgzSettings
     * @throws Exception если передача, сравнение или запись результатов не удались
     */
    public static void main(String[] commandLineArguments) throws Exception {
        String mode = ConsoleInput.readLine("Эксперимент: 1 — локальный, 2 — другой компьютер [1]: ");
        if (mode.equals("2")) {
            runRemoteExperiment();
            return;
        }
        if (!mode.isBlank() && !mode.equals("1")) {
            throw new IllegalArgumentException("Выберите 1 или 2");
        }
        TransferReportWriter report = new TransferReportWriter(
                "Локальный эксперимент: один компьютер, одна JVM, два потока, TCP IPv4 loopback 127.0.0.1. "
                        + "Прогрев: " + RgzSettings.TRANSFER_WARMUP_COUNT + "; повторов на размер: "
                        + RgzSettings.TRANSFER_REPETITIONS + ". Создание файлов и сравнение вне измерений."
        );
        Path inputDirectory = report.reportDirectory().resolve("inputs");
        Path outputDirectory = report.reportDirectory().resolve("received");
        Files.createDirectories(inputDirectory);
        KeyAgreementProtocolFactory factory = new KeyAgreementProtocolFactory(
                RgzSettings.KEY_AGREEMENT_GROUP_SETTINGS, RgzSettings.DATA_PATHS
        );
        // Первая генерация параметров и долговременных файлов не входит в измерения.
        factory.createServerProtocol(ProtocolType.MQV);
        ExecutorService receiverThread = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(); FileReceiver receiver = new FileReceiver(outputDirectory)) {
            listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            listener.setSoTimeout(RgzSettings.READ_TIMEOUT_MILLISECONDS);
            for (int repetition = 1; repetition <= RgzSettings.TRANSFER_WARMUP_COUNT; repetition++) {
                transferOneFile(inputDirectory, 1048576, repetition, true, factory, listener,
                        receiver, receiverThread, report);
            }
            for (long size : RgzSettings.TRANSFER_FILE_SIZES) {
                for (int repetition = 1; repetition <= RgzSettings.TRANSFER_REPETITIONS; repetition++) {
                    transferOneFile(inputDirectory, size, repetition, false, factory, listener,
                            receiver, receiverThread, report);
                }
            }
        } finally {
            receiverThread.shutdownNow();
            if (!receiverThread.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("Поток получателя ещё завершает сетевую операцию");
            }
        }
        System.out.println("Эксперимент завершён. CSV и HTML: " + report.reportDirectory());
    }

    /**
     * Запускает отдельный MQV-сеанс, сопоставляет два конца и проверяет содержимое вне таймеров.
     * @param inputDirectory каталог временных искусственных входов
     * @param size размер файла
     * @param repetition номер повтора
     * @param warmup признак прогрева
     * @param factory общие параметры и доверенные материалы
     * @param listener локальный TCP-сервер
     * @param receiver владелец каталога приёма
     * @param receiverThread отдельный поток приёма
     * @param report запись наблюдений
     * @throws Exception при любой незавершённой передаче или несовпадении файлов
     */
    private static void transferOneFile(Path inputDirectory, long size, int repetition, boolean warmup,
                                        KeyAgreementProtocolFactory factory, ServerSocket listener,
                                        FileReceiver receiver, ExecutorService receiverThread,
                                        TransferReportWriter report) throws Exception {
        String fileName = (warmup ? "warmup-" : "file-") + size + "-" + repetition + ".bin";
        Path source = inputDirectory.resolve(fileName);
        createFile(source, size);
        String expectedHash = FileChecksum.sha256(source);
        TransferMeasurement receivedMeasurement = new TransferMeasurement();
        long preparationStarted = System.nanoTime();
        KeyAgreementProtocol receiverProtocol = factory.createServerProtocol(ProtocolType.MQV);
        receivedMeasurement.finishInterval(TransferPhase.PREPARATION, preparationStarted);
        Future<Path> receivedFile = receiverThread.submit(() -> receiveOneFile(listener, receiver,
                receiverProtocol, receivedMeasurement));
        TransferMeasurement sentMeasurement = sendOneFile(source, "127.0.0.1", listener.getLocalPort(), factory);
        Path received = receivedFile.get(RgzSettings.READ_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
        if (Files.mismatch(source, received) != -1 || !expectedHash.equals(FileChecksum.sha256(received))) {
            throw new IOException("Побайтовая проверка или SHA-256 не прошли: " + fileName);
        }
        String verification = "HMAC + ACK + byte comparison + SHA-256=" + expectedHash;
        report.append(sentMeasurement, "sender", "127.0.0.1", repetition, warmup, verification);
        report.append(receivedMeasurement, "receiver", "127.0.0.1", repetition, warmup, verification);
        Files.delete(source);
        Files.delete(received);
        System.out.printf("%s %d байт, повтор %d: совпадение подтверждено%n", warmup ? "Прогрев" : "Замер", size, repetition);
    }

    /**
     * Отправляет ту же серию действующему FileReceiverMain на другом компьютере.
     * CSV сторон сопоставляют по уникальному имени и SHA-256, часы не синхронизируют.
     * @throws Exception если доверие не подготовлено или передача не подтверждена
     */
    private static void runRemoteExperiment() throws Exception {
        String host = ConsoleInput.readLine("Адрес второго компьютера: ");
        if (host.isBlank() || InetAddress.getByName(host).isLoopbackAddress()
                || InetAddress.getByName(host).isAnyLocalAddress()) {
            throw new IllegalArgumentException("Для этого режима нужен адрес второго компьютера");
        }
        String portText = ConsoleInput.readLine("Порт [" + RgzSettings.FILE_TRANSFER_PORT + "]: ");
        int port = portText.isBlank() ? RgzSettings.FILE_TRANSFER_PORT : Integer.parseInt(portText);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Порт должен быть 1…65535");
        }
        TransferReportWriter report = new TransferReportWriter("Серия к адресу " + host
                + "; физическую топологию фиксируют участники опыта. Прогрев: "
                + RgzSettings.TRANSFER_WARMUP_COUNT + "; повторов на размер: " + RgzSettings.TRANSFER_REPETITIONS);
        Path inputDirectory = report.reportDirectory().resolve("inputs");
        Files.createDirectories(inputDirectory);
        KeyAgreementProtocolFactory factory = new KeyAgreementProtocolFactory(
                RgzSettings.KEY_AGREEMENT_GROUP_SETTINGS, RgzSettings.DATA_PATHS
        );
        String runPrefix = report.reportDirectory().getFileName().toString();
        for (int sizeIndex = -1; sizeIndex < RgzSettings.TRANSFER_FILE_SIZES.size(); sizeIndex++) {
            boolean warmup = sizeIndex < 0;
            long size = warmup ? 1048576 : RgzSettings.TRANSFER_FILE_SIZES.get(sizeIndex);
            int repeats = warmup ? RgzSettings.TRANSFER_WARMUP_COUNT : RgzSettings.TRANSFER_REPETITIONS;
            for (int repetition = 1; repetition <= repeats; repetition++) {
                Path source = inputDirectory.resolve(runPrefix + (warmup ? "-warmup-" : "-file-")
                        + size + "-" + repetition + ".bin");
                createFile(source, size);
                String expectedHash = FileChecksum.sha256(source);
                TransferMeasurement measurement = sendOneFile(source, host, port, factory);
                report.append(measurement, "sender", host, repetition, warmup,
                        "authenticated ACK; source SHA-256=" + expectedHash);
                Files.delete(source);
                System.out.printf("Передано %d байт, повтор %d%n", size, repetition);
            }
        }
        System.out.println("CSV и HTML отправителя: " + report.reportDirectory());
        System.out.println("Для сравнения SHA-256 соберите также CSV получателя.");
    }

    /**
     * Устанавливает MQV и отправляет один файл, одинаково для локальной и удалённой серий.
     * @param source исходный файл
     * @param host адрес получателя
     * @param port порт получателя
     * @param factory фабрика клиента с доверенными материалами Bob
     * @return завершённые замеры отправителя
     * @throws IOException при ошибке сети или файла
     * @throws GeneralSecurityException при ошибке проверки доверия
     */
    private static TransferMeasurement sendOneFile(Path source, String host, int port,
                                                  KeyAgreementProtocolFactory factory)
            throws IOException, GeneralSecurityException {
        TransferMeasurement sentMeasurement = new TransferMeasurement();
        try (Socket connection = new Socket()) {
            long connectionStarted = System.nanoTime();
            connection.connect(new InetSocketAddress(host, port),
                    RgzSettings.CONNECT_TIMEOUT_MILLISECONDS);
            sentMeasurement.finishInterval(TransferPhase.CONNECTION, connectionStarted);
            connection.setSoTimeout(RgzSettings.READ_TIMEOUT_MILLISECONDS);
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(connection.getInputStream(),
                    RgzSettings.FILE_BUFFER_LENGTH_BYTES));
                 DataOutputStream output = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream(),
                         RgzSettings.FILE_BUFFER_LENGTH_BYTES))) {
                long keyStarted = System.nanoTime();
                try (FileTransferSession session = FileTransferSession.establishSender(input, output, factory)) {
                    sentMeasurement.finishInterval(TransferPhase.KEY_ESTABLISHMENT, keyStarted);
                    new FileSender(new InitializationVectorSequence(RgzSettings.IV_SEQUENCE_FILE))
                            .send(source, connection, input, output, session, sentMeasurement);
                }
            }
        }
        return sentMeasurement;
    }

    /**
     * Принимает одно соединение в выделенном потоке без включения accept в фазу MQV.
     * @param listener локальный сервер
     * @param receiver получатель файлов
     * @param protocol заранее подготовленный MQV
     * @param measurement замеры получателя
     * @return путь принятого файла
     * @throws IOException при ошибке сети, сеанса или файла
     */
    private static Path receiveOneFile(ServerSocket listener, FileReceiver receiver,
                                       KeyAgreementProtocol protocol, TransferMeasurement measurement) throws IOException {
        try (Socket connection = listener.accept();
             DataInputStream input = new DataInputStream(new BufferedInputStream(connection.getInputStream(),
                     RgzSettings.FILE_BUFFER_LENGTH_BYTES));
             DataOutputStream output = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream(),
                     RgzSettings.FILE_BUFFER_LENGTH_BYTES))) {
            connection.setSoTimeout(RgzSettings.READ_TIMEOUT_MILLISECONDS);
            long keyStarted = System.nanoTime();
            try (FileTransferSession session = FileTransferSession.establishReceiver(input, output, protocol)) {
                measurement.finishInterval(TransferPhase.KEY_ESTABLISHMENT, keyStarted);
                return receiver.receive(input, output, session, measurement);
            }
        }
    }

    /**
     * Создаёт воспроизводимые искусственные байты порциями, без массива размером с файл.
     * @param file новый файл эксперимента
     * @param size точное число байтов
     * @throws IOException если запись не удалась
     */
    private static void createFile(Path file, long size) throws IOException {
        Random generator = new Random(20261019L + size);
        byte[] buffer = new byte[RgzSettings.FILE_BUFFER_LENGTH_BYTES];
        try (OutputStream output = Files.newOutputStream(file)) {
            long remaining = size;
            while (remaining > 0) {
                generator.nextBytes(buffer);
                int count = (int) Math.min(buffer.length, remaining);
                output.write(buffer, 0, count);
                remaining -= count;
            }
        }
    }

}
