package securetransfer.rgz.network;

import securetransfer.rgz.cipher.StreamCipherAlgorithm;
import securetransfer.rgz.file.FileHeader;

import javax.crypto.Mac;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Формат версии 1: начало сеанса, подтверждение ключей, заголовок файла и ответ получателя. */
public final class FileTransferProtocol {
    /** Четыре байта STFS, отличающие файловый сеанс от первой лабораторной. */
    private static final int SESSION_MAGIC = 0x53544653;

    /** Четыре байта STFH в заголовке файла. */
    private static final int FILE_MAGIC = 0x53544648;

    /** Верхняя граница заголовка до выделения памяти по сетевой длине. */
    private static final int MAXIMUM_HEADER_LENGTH_BYTES = 1024;

    /** Верхняя граница ответа и его текстового сообщения. */
    private static final int MAXIMUM_RESPONSE_LENGTH_BYTES = 2048;

    /** Запрещает создание экземпляров класса кодирования. */
    private FileTransferProtocol() {
    }

    /**
     * Передаёт обозначение приложения и версию перед общим обменом MQV.
     *
     * @param output сетевой выход
     * @throws IOException если запись не удалась
     */
    public static void sendSessionPreamble(DataOutputStream output) throws IOException {
        output.writeInt(SESSION_MAGIC);
        output.writeInt(FileHeader.CURRENT_VERSION);
        output.flush();
    }

    /**
     * Проверяет обозначение приложения и единственную поддерживаемую версию.
     *
     * @param input сетевой вход
     * @throws IOException если формат неверен или соединение оборвалось
     */
    public static void receiveSessionPreamble(DataInputStream input) throws IOException {
        if (input.readInt() != SESSION_MAGIC || input.readInt() != FileHeader.CURRENT_VERSION) {
            throw new IOException("Несовместимый протокол передачи файлов");
        }
    }

    /**
     * Подтверждает владение ключом сеанса с отдельной меткой роли.
     *
     * @param output сетевой выход
     * @param session подготовленные ключи
     * @param serverConfirmation true для сервера, false для клиента
     * @throws IOException если запись не удалась
     */
    public static void sendConfirmation(
            DataOutputStream output, FileTransferSession session, boolean serverConfirmation
    ) throws IOException {
        output.write(session.authenticateControlMessage(
                serverConfirmation ? "server-confirm" : "client-confirm", new byte[0]));
        output.flush();
    }

    /**
     * Проверяет подтверждение ключа и ожидаемую роль отправителя.
     *
     * @param input сетевой вход
     * @param session подготовленные ключи
     * @param serverConfirmation true при ожидании подтверждения сервера
     * @throws IOException если подтверждение неверно или не получено целиком
     */
    public static void receiveConfirmation(
            DataInputStream input, FileTransferSession session, boolean serverConfirmation
    ) throws IOException {
        byte[] receivedTag = readAuthenticationTag(input);
        byte[] expectedTag = session.authenticateControlMessage(
                serverConfirmation ? "server-confirm" : "client-confirm", new byte[0]);
        if (!MessageDigest.isEqual(expectedTag, receivedTag)) {
            throw new IOException("Не подтверждено совпадение ключей сеанса MQV");
        }
    }

    /**
     * Передаёт заголовок и добавляет его точное сетевое представление в HMAC файла.
     *
     * @param output сетевой выход
     * @param header метаданные и IV
     * @param fileMac состояние HMAC файла
     * @throws IOException если кодирование или запись не удались
     */
    public static void sendHeader(DataOutputStream output, FileHeader header, Mac fileMac) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream encoded = new DataOutputStream(bytes)) {
            encoded.writeInt(FILE_MAGIC);
            encoded.writeInt(header.protocolVersion());
            writeString(encoded, header.fileName(), FileHeader.MAXIMUM_FILE_NAME_BYTES);
            encoded.writeLong(header.fileSizeBytes());
            encoded.writeInt(header.cipherAlgorithm().getProtocolId());
            byte[] vector = header.initializationVector();
            encoded.writeInt(vector.length);
            encoded.write(vector);
        }
        byte[] payload = bytes.toByteArray();
        fileMac.update(encodeLength(payload.length));
        fileMac.update(payload);
        writeFrame(output, payload, MAXIMUM_HEADER_LENGTH_BYTES);
    }

    /**
     * Читает ограниченный заголовок и включает исходные байты в HMAC без перекодирования.
     *
     * @param input сетевой вход
     * @param fileMac состояние HMAC файла
     * @return структурно проверенные метаданные; целостность подтверждается после тела
     * @throws IOException если версия, поля или длины неверны
     */
    public static FileHeader receiveHeader(DataInputStream input, Mac fileMac) throws IOException {
        byte[] payload = readFrame(input, MAXIMUM_HEADER_LENGTH_BYTES);
        fileMac.update(encodeLength(payload.length));
        fileMac.update(payload);
        try (DataInputStream encoded = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (encoded.readInt() != FILE_MAGIC) {
                throw new IOException("Неверный заголовок файла");
            }
            int version = encoded.readInt();
            String fileName = readString(encoded, FileHeader.MAXIMUM_FILE_NAME_BYTES);
            long fileSize = encoded.readLong();
            StreamCipherAlgorithm algorithm = StreamCipherAlgorithm.fromProtocolId(encoded.readInt());
            int vectorLength = encoded.readInt();
            if (vectorLength != algorithm.createCipher().getInitializationVectorLengthBytes()) {
                throw new IOException("Неверная длина IV в заголовке");
            }
            byte[] vector = new byte[vectorLength];
            encoded.readFully(vector);
            if (encoded.read() != -1) {
                throw new IOException("Лишние поля в заголовке файла");
            }
            return new FileHeader(version, fileName, fileSize, algorithm, vector);
        } catch (IllegalArgumentException invalidHeader) {
            throw new IOException("Недопустимый заголовок: " + invalidHeader.getMessage(), invalidHeader);
        }
    }

    /**
     * Отправляет подтверждение или отказ с HMAC отдельного назначения.
     *
     * @param output сетевой выход
     * @param session текущий сеанс
     * @param accepted true только после проверки и публикации файла
     * @param message описание результата
     * @throws IOException если запись не удалась
     */
    public static void sendAcknowledgement(
            DataOutputStream output, FileTransferSession session, boolean accepted, String message
    ) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream encoded = new DataOutputStream(bytes)) {
            encoded.writeBoolean(accepted);
            writeString(encoded, message, MAXIMUM_RESPONSE_LENGTH_BYTES - 5);
        }
        byte[] payload = bytes.toByteArray();
        writeFrame(output, payload, MAXIMUM_RESPONSE_LENGTH_BYTES);
        output.write(session.authenticateControlMessage("acknowledgement", payload));
        output.flush();
    }

    /**
     * Проверяет подлинность ответа и возвращает результат приёма.
     *
     * @param input сетевой вход
     * @param session текущий сеанс
     * @return подтверждение либо отказ получателя
     * @throws IOException если ответ неверен или не получен
     */
    public static Acknowledgement receiveAcknowledgement(DataInputStream input, FileTransferSession session) throws IOException {
        byte[] payload = readFrame(input, MAXIMUM_RESPONSE_LENGTH_BYTES);
        byte[] receivedTag = readAuthenticationTag(input);
        if (!MessageDigest.isEqual(session.authenticateControlMessage("acknowledgement", payload), receivedTag)) {
            throw new IOException("Не подтверждена подлинность ответа получателя");
        }
        try (DataInputStream encoded = new DataInputStream(new ByteArrayInputStream(payload))) {
            int status = encoded.readUnsignedByte();
            String message = readString(encoded, MAXIMUM_RESPONSE_LENGTH_BYTES - 5);
            if ((status != 0 && status != 1) || encoded.read() != -1) {
                throw new IOException("Неверный формат ответа получателя");
            }
            return new Acknowledgement(status == 1, message);
        }
    }

    /**
     * Получает ровно один тег HMAC-SHA256.
     *
     * @param input сетевой вход
     * @return 32 байта тега
     * @throws IOException если соединение оборвалось
     */
    public static byte[] readAuthenticationTag(DataInputStream input) throws IOException {
        byte[] tag = new byte[FileTransferSession.AUTHENTICATION_TAG_LENGTH_BYTES];
        try {
            input.readFully(tag);
        } catch (EOFException incompleteTag) {
            throw new IOException("Соединение оборвалось до получения полного HMAC", incompleteTag);
        }
        return tag;
    }

    /**
     * Записывает ограниченный кадр с 32-битной длиной.
     *
     * @param output сетевой выход
     * @param payload содержимое кадра
     * @param maximumLength верхняя граница длины
     * @throws IOException если длина или запись неверны
     */
    private static void writeFrame(DataOutputStream output, byte[] payload, int maximumLength) throws IOException {
        if (payload.length > maximumLength) {
            throw new IOException("Слишком длинный кадр протокола");
        }
        output.writeInt(payload.length);
        output.write(payload);
    }

    /**
     * Проверяет длину до выделения памяти и читает кадр целиком.
     *
     * @param input сетевой вход
     * @param maximumLength верхняя граница длины
     * @return содержимое кадра
     * @throws IOException если кадр недопустим или неполон
     */
    private static byte[] readFrame(DataInputStream input, int maximumLength) throws IOException {
        try {
            int length = input.readInt();
            if (length < 0 || length > maximumLength) {
                throw new IOException("Недопустимая длина кадра");
            }
            byte[] payload = new byte[length];
            input.readFully(payload);
            return payload;
        } catch (EOFException incompleteFrame) {
            throw new IOException("Соединение оборвалось до получения полного кадра", incompleteFrame);
        }
    }

    /**
     * Записывает строку UTF-8 с длиной в байтах.
     *
     * @param output выходной поток
     * @param value строка
     * @param maximumLength верхняя граница байтов
     * @throws IOException если строка слишком длинна
     */
    private static void writeString(DataOutputStream output, String value, int maximumLength) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumLength) {
            throw new IOException("Слишком длинная строка протокола");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    /**
     * Читает UTF-8 без молчаливой замены повреждённых последовательностей.
     *
     * @param input входной поток
     * @param maximumLength верхняя граница байтов
     * @return декодированная строка
     * @throws IOException если длина, данные или кодировка неверны
     */
    private static String readString(DataInputStream input, int maximumLength) throws IOException {
        byte[] bytes = readFrame(input, maximumLength);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    /**
     * Кодирует длину в том же порядке байтов, что DataOutputStream.writeInt.
     *
     * @param length длина кадра
     * @return четыре байта старшим вперёд
     */
    private static byte[] encodeLength(int length) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(length).array();
    }

    /**
     * Проверенный ответ в пределах одного сеанса и одного файла.
     *
     * @param accepted true после проверки и сохранения файла
     * @param message текст результата
     */
    public record Acknowledgement(boolean accepted, String message) {
    }
}
