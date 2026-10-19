package securetransfer.rgz.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Постоянная последовательность IV, общая для всех ключей отправителя.
 *
 * <p>Следующий номер добавляется в журнал и force выполняется до выдачи IV.
 * Журнал дописывается без усечения. Неполная или неверная запись останавливает
 * резервирование. Счётчик не сбрасывается после неудачной передачи.</p>
 */
public final class InitializationVectorSequence {
    /** Заголовок журнала резервирования. */
    private static final String JOURNAL_HEADER = "SECURETRANSFER_IV_SEQUENCE_V1";

    /** Защита от одновременного резервирования разными объектами одной JVM. */
    private static final Object RESERVATION_LOCK = new Object();

    /** Путь к постоянному журналу, не содержащему секретных ключей. */
    private final Path journalPath;

    /**
     * Задаёт расположение журнала.
     *
     * @param journalPath файл состояния отправителя
     */
    public InitializationVectorSequence(Path journalPath) {
        this.journalPath = Objects.requireNonNull(journalPath).toAbsolutePath().normalize();
    }

    /**
     * Резервирует следующий номер под межпроцессной блокировкой и превращает его в IV.
     *
     * @param vectorLengthBytes длина IV выбранного шифра: 8 или 16 байт
     * @return новый IV; номер уже записан на диск и не будет использован повторно
     * @throws IOException если журнал повреждён, недоступен или номера исчерпаны
     */
    public byte[] reserveNext(int vectorLengthBytes) throws IOException {
        if (vectorLengthBytes != 8 && vectorLengthBytes != 16) {
            throw new IllegalArgumentException("Поддерживаются IV длиной 8 или 16 байт");
        }
        synchronized (RESERVATION_LOCK) {
            Files.createDirectories(journalPath.getParent());
            try (FileChannel journal = FileChannel.open(journalPath,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock journalLock = journal.lock()) {
                if (!journalLock.isValid()) {
                    throw new IOException("Не удалось заблокировать журнал IV");
                }
                BigInteger nextNumber = readLastNumber(journal).add(BigInteger.ONE);
                if (nextNumber.bitLength() > vectorLengthBytes * Byte.SIZE) {
                    throw new IOException("Последовательность IV исчерпана; повторное использование запрещено");
                }
                String newRecord = (journal.size() == 0 ? JOURNAL_HEADER + "\n" : "") + nextNumber + "\n";
                journal.position(journal.size());
                ByteBuffer recordBytes = StandardCharsets.US_ASCII.encode(newRecord);
                while (recordBytes.hasRemaining()) {
                    journal.write(recordBytes);
                }
                journal.force(true);

                byte[] numberBytes = nextNumber.toByteArray();
                int significantOffset = numberBytes[0] == 0 ? 1 : 0;
                int significantLength = numberBytes.length - significantOffset;
                byte[] vector = new byte[vectorLengthBytes];
                System.arraycopy(numberBytes, significantOffset, vector,
                        vector.length - significantLength, significantLength);
                return vector;
            }
        }
    }

    /**
     * Читает только последовательный журнал с завершённой последней строкой.
     * Обёртка Reader не закрывается отдельно: каналом владеет reserveNext.
     *
     * @param journal заблокированный канал журнала
     * @return последний выданный номер либо ноль для нового журнала
     * @throws IOException если формат или последовательность записей нарушены
     */
    private BigInteger readLastNumber(FileChannel journal) throws IOException {
        if (journal.size() == 0) {
            return BigInteger.ZERO;
        }
        ByteBuffer lastByte = ByteBuffer.allocate(1);
        if (journal.read(lastByte, journal.size() - 1) != 1 || lastByte.array()[0] != '\n') {
            throw new IOException("Журнал IV содержит незавершённую запись: " + journalPath);
        }
        journal.position(0);
        BufferedReader reader = new BufferedReader(Channels.newReader(journal, StandardCharsets.US_ASCII));
        if (!JOURNAL_HEADER.equals(reader.readLine())) {
            throw new IOException("Неверный заголовок журнала IV: " + journalPath);
        }
        BigInteger previousNumber = BigInteger.ZERO;
        String record;
        while ((record = reader.readLine()) != null) {
            if (!record.matches("[1-9][0-9]{0,38}")) {
                throw new IOException("Повреждена запись журнала IV: " + journalPath);
            }
            BigInteger number = new BigInteger(record);
            if (!number.equals(previousNumber.add(BigInteger.ONE))) {
                throw new IOException("Нарушена последовательность журнала IV: " + journalPath);
            }
            previousNumber = number;
        }
        return previousNumber;
    }
}
