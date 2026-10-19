package securetransfer.rgz.network;

import securetransfer.rgz.cipher.StreamCipher;
import securetransfer.rgz.measurement.TransferMeasurement;
import securetransfer.rgz.measurement.TransferPhase;
import securetransfer.rgz.file.FileCipher;
import securetransfer.rgz.file.FileHeader;

import javax.crypto.Mac;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;

/**
 * Приём в служебный .part и публикация файла только после проверки целостности.
 *
 * <p>На время работы каталог занят одним получателем. После завершения прежнего
 * процесса следующий запуск удаляет оставшиеся служебные .part из этого каталога.</p>
 */
public final class FileReceiver implements AutoCloseable {
    /** Каталог готовых файлов с разрешёнными физическими путями. */
    private final Path receivingDirectory;

    /** Служебный каталог на той же файловой системе, что готовые файлы. */
    private final Path stagingDirectory;

    /** Канал межпроцессной блокировки каталога. */
    private final FileChannel directoryLockChannel;

    /** Блокировка, исключающая удаление .part другого активного получателя. */
    private final FileLock directoryLock;

    /**
     * Готовит каталог и восстанавливает состояние после прерванного процесса.
     *
     * @param receivingDirectory каталог назначения
     * @throws IOException если каталог недоступен, занят или очистка не удалась
     */
    public FileReceiver(Path receivingDirectory) throws IOException {
        Files.createDirectories(receivingDirectory);
        this.receivingDirectory = receivingDirectory.toRealPath();
        stagingDirectory = this.receivingDirectory.resolve(".securetransfer-incoming");
        if (Files.isSymbolicLink(stagingDirectory)) {
            throw new IOException("Служебный каталог приёма не должен быть символической ссылкой");
        }
        Files.createDirectories(stagingDirectory);
        directoryLockChannel = FileChannel.open(stagingDirectory.resolve("receiver.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquiredLock = null;
        try {
            acquiredLock = directoryLockChannel.tryLock();
            if (acquiredLock == null) {
                throw new IOException("В этот каталог уже принимает файлы другой процесс");
            }
            recoverIncompleteFiles();
        } catch (IOException | RuntimeException initializationFailure) {
            if (acquiredLock != null) {
                try {
                    acquiredLock.release();
                } catch (IOException releaseFailure) {
                    initializationFailure.addSuppressed(releaseFailure);
                }
            }
            try {
                directoryLockChannel.close();
            } catch (IOException closeFailure) {
                initializationFailure.addSuppressed(closeFailure);
            }
            if (initializationFailure instanceof OverlappingFileLockException) {
                throw new IOException("Каталог уже используется получателем этой JVM", initializationFailure);
            }
            throw initializationFailure;
        }
        directoryLock = acquiredLock;
    }

    /**
     * Читает один файл, проверяет HMAC и конец запроса, затем отправляет подтверждение.
     *
     * @param input сетевой вход
     * @param output сетевой выход для подтверждения
     * @param session подтверждённый сеанс MQV
     * @return путь опубликованного файла
     * @throws IOException если файл не принят или подтверждение не удалось отправить
     */
    public Path receive(DataInputStream input, DataOutputStream output, FileTransferSession session) throws IOException {
        return receive(input, output, session, new TransferMeasurement());
    }

    /**
     * Принимает файл и измеряет расшифрование отдельно от сети и диска.
     * @param input сетевой вход
     * @param output выход подтверждения
     * @param session подтверждённый сеанс
     * @param measurement замеры текущего получателя
     * @return путь готового файла
     * @throws IOException если приём или подтверждение не удались
     */
    public Path receive(DataInputStream input, DataOutputStream output, FileTransferSession session,
                        TransferMeasurement measurement) throws IOException {
        long transferStarted = System.nanoTime();
        if (!directoryLock.isValid()) {
            throw new IllegalStateException("Получатель уже закрыт");
        }
        Path temporaryFile = null;
        Path completedFile;
        try {
            Mac fileMac = session.createFileAuthenticationMac();
            long headerStarted = System.nanoTime();
            FileHeader header = FileTransferProtocol.receiveHeader(input, fileMac);
            measurement.finishInterval(TransferPhase.NETWORK_IO, headerStarted);
            measurement.identifyFile(header.fileName(), header.fileSizeBytes());
            long setupStarted = System.nanoTime();
            StreamCipher cipher = session.createFileCipher(header);
            measurement.finishInterval(TransferPhase.CIPHER_SETUP, setupStarted);
            completedFile = receivingDirectory.resolve(header.fileName());
            if (Files.exists(completedFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException("Файл уже существует: " + header.fileName());
            }
            temporaryFile = Files.createTempFile(stagingDirectory, "receive-", ".part");
            try (FileChannel fileChannel = FileChannel.open(temporaryFile, StandardOpenOption.WRITE);
                 OutputStream plaintext = Channels.newOutputStream(fileChannel)) {
                FileCipher.decrypt(input, plaintext, header.fileSizeBytes(), cipher, fileMac, measurement);
                fileChannel.force(true);
            }
            long tagStarted = System.nanoTime();
            byte[] receivedTag = FileTransferProtocol.readAuthenticationTag(input);
            measurement.finishInterval(TransferPhase.NETWORK_IO, tagStarted);
            if (!MessageDigest.isEqual(fileMac.doFinal(), receivedTag)) {
                throw new IOException("HMAC файла не совпал: заголовок или данные повреждены");
            }
            long endingStarted = System.nanoTime();
            int endingByte = input.read();
            measurement.finishInterval(TransferPhase.NETWORK_IO, endingStarted);
            if (endingByte != -1) {
                throw new IOException("Получены лишние данные после файла и HMAC");
            }
            Files.move(temporaryFile, completedFile);
            temporaryFile = null;
        } catch (IOException | RuntimeException receptionFailure) {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupFailure) {
                    receptionFailure.addSuppressed(cleanupFailure);
                }
            }
            try {
                FileTransferProtocol.sendAcknowledgement(output, session, false,
                        "Файл не принят: ошибка формата, целостности, доступа или обрыв передачи");
            } catch (IOException | RuntimeException acknowledgementFailure) {
                receptionFailure.addSuppressed(acknowledgementFailure);
            }
            throw receptionFailure;
        }

        try {
            long acknowledgementStarted = System.nanoTime();
            FileTransferProtocol.sendAcknowledgement(output, session, true,
                    "Файл «" + completedFile.getFileName() + "» принят и проверен");
            measurement.finishInterval(TransferPhase.NETWORK_IO, acknowledgementStarted);
        } catch (IOException acknowledgementFailure) {
            throw new IOException("Файл сохранён: " + completedFile
                    + ", но подтверждение отправить не удалось", acknowledgementFailure);
        }
        measurement.finishInterval(TransferPhase.TRANSFER_ELAPSED, transferStarted);
        return completedFile;
    }

    /**
     * Удаляет только служебные файлы незавершённого приёма при удерживаемой блокировке каталога.
     *
     * @throws IOException если удалить остатки прежнего процесса не удалось
     */
    private void recoverIncompleteFiles() throws IOException {
        int deletedCount = 0;
        try (DirectoryStream<Path> incompleteFiles = Files.newDirectoryStream(stagingDirectory, "receive-*.part")) {
            for (Path incompleteFile : incompleteFiles) {
                Files.delete(incompleteFile);
                deletedCount++;
            }
        }
        if (deletedCount > 0) {
            System.out.println("Удалены незавершённые временные файлы прошлого запуска: " + deletedCount);
        }
    }

    /** Освобождает блокировку каталога; готовые файлы сохраняются. */
    @Override
    public void close() throws IOException {
        try {
            directoryLock.release();
        } finally {
            directoryLockChannel.close();
        }
    }
}
