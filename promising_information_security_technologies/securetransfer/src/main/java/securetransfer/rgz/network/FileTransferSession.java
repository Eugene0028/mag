package securetransfer.rgz.network;

import securetransfer.common.network.KeyExchangeMessage;
import securetransfer.common.network.KeyExchangeTransport;
import securetransfer.common.protocol.KeyAgreementProtocol;
import securetransfer.common.protocol.KeyAgreementProtocolFactory;
import securetransfer.common.protocol.ProtocolType;
import securetransfer.common.protocol.PublicExchangeData;
import securetransfer.rgz.cipher.StreamCipher;
import securetransfer.rgz.config.RgzSettings;
import securetransfer.rgz.file.FileHeader;
import securetransfer.rgz.key.MqvKeyDerivation;
import securetransfer.rgz.key.Sha3Hasher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Один сеанс MQV для одного файла: выработка ключей, подтверждение и создание шифра/HMAC.
 *
 * <p>Получатель — сервер Alice, отправитель — клиент Bob. Проверки параметров,
 * подписанного справочника и открытых ключей выполняются общим кодом первой лабы.</p>
 */
public final class FileTransferSession implements AutoCloseable {
    /** Длина HMAC-SHA256 во всех сообщениях протокола. */
    public static final int AUTHENTICATION_TAG_LENGTH_BYTES = 32;

    /** Ключ выбранного потокового шифра. */
    private final byte[] encryptionKey;

    /** Отдельный ключ HMAC, связанный с данным открытым обменом. */
    private final byte[] authenticationKey;

    /** Хеш открытых сообщений в одинаковом для обеих сторон порядке. */
    private final byte[] exchangeTranscriptHash;

    /** Признак завершения использования ключей. */
    private boolean closed;

    /**
     * Принимает владение массивами только из внутреннего метода выработки ключей.
     *
     * @param encryptionKey ключ шифрования
     * @param authenticationKey ключ HMAC
     * @param exchangeTranscriptHash хеш открытого обмена
     */
    private FileTransferSession(byte[] encryptionKey, byte[] authenticationKey, byte[] exchangeTranscriptHash) {
        this.encryptionKey = encryptionKey;
        this.authenticationKey = authenticationKey;
        this.exchangeTranscriptHash = exchangeTranscriptHash;
    }

    /**
     * Передаёт открытые данные сервера, получает клиентские и подтверждает совпадение ключей.
     *
     * @param input сетевой вход
     * @param output сетевой выход
     * @param receiverProtocol новый экземпляр MQV из общей фабрики
     * @return подготовленный сеанс; вызывающий код обязан его закрыть
     * @throws IOException если обмен или подтверждение не удались
     */
    public static FileTransferSession establishReceiver(
            DataInputStream input, DataOutputStream output, KeyAgreementProtocol receiverProtocol
    ) throws IOException {
        FileTransferProtocol.sendSessionPreamble(output);
        PublicExchangeData serverData = receiverProtocol.getPublicExchangeData();
        KeyExchangeTransport.sendMessage(output, new KeyExchangeMessage(ProtocolType.MQV, serverData));
        KeyExchangeMessage clientMessage = KeyExchangeTransport.receiveMessage(input);
        requireMqv(clientMessage);
        BigInteger sharedSecret = receiverProtocol.calculateSharedSecret(clientMessage.publicExchangeData());
        FileTransferSession session = createSession(sharedSecret, serverData, clientMessage.publicExchangeData());
        try {
            FileTransferProtocol.sendConfirmation(output, session, true);
            FileTransferProtocol.receiveConfirmation(input, session, false);
            return session;
        } catch (IOException | RuntimeException failure) {
            session.close();
            throw failure;
        }
    }

    /**
     * Проверяет открытые данные сервера, подготавливает клиента и подтверждает общий ключ.
     *
     * @param input сетевой вход
     * @param output сетевой выход
     * @param protocolFactory общая фабрика с настройками РГЗ и файлами доверия Bob
     * @return подготовленный сеанс; вызывающий код обязан его закрыть
     * @throws IOException если формат, файлы или подтверждение неверны
     * @throws GeneralSecurityException если подпись справочника неверна
     */
    public static FileTransferSession establishSender(
            DataInputStream input, DataOutputStream output, KeyAgreementProtocolFactory protocolFactory
    ) throws IOException, GeneralSecurityException {
        FileTransferProtocol.receiveSessionPreamble(input);
        KeyExchangeMessage serverMessage = KeyExchangeTransport.receiveMessage(input);
        requireMqv(serverMessage);
        KeyAgreementProtocol senderProtocol = protocolFactory.createClientProtocol(
                ProtocolType.MQV, serverMessage.publicExchangeData().groupParameters()
        );
        PublicExchangeData clientData = senderProtocol.getPublicExchangeData();
        BigInteger sharedSecret = senderProtocol.calculateSharedSecret(serverMessage.publicExchangeData());
        KeyExchangeTransport.sendMessage(output, new KeyExchangeMessage(ProtocolType.MQV, clientData));
        FileTransferSession session = createSession(sharedSecret, serverMessage.publicExchangeData(), clientData);
        try {
            FileTransferProtocol.receiveConfirmation(input, session, true);
            FileTransferProtocol.sendConfirmation(output, session, false);
            return session;
        } catch (IOException | RuntimeException failure) {
            session.close();
            throw failure;
        }
    }

    /**
     * Создаёт шифр текущего файла с выработанным ключом и IV из заголовка.
     *
     * @param header проверенный заголовок файла
     * @return инициализированный шифр с позицией в начале потока
     */
    public StreamCipher createFileCipher(FileHeader header) {
        requireOpen();
        if (header.cipherAlgorithm() != RgzSettings.FILE_CIPHER) {
            throw new IllegalArgumentException("Приложение принимает только шифр " + RgzSettings.FILE_CIPHER);
        }
        StreamCipher cipher = header.cipherAlgorithm().createCipher();
        cipher.initialize(encryptionKey, header.initializationVector());
        return cipher;
    }

    /** @return HMAC для заголовка и шифротекста с привязкой к сеансу */
    public Mac createFileAuthenticationMac() {
        return createAuthenticationMac("file-data");
    }

    /**
     * Вычисляет HMAC отдельного служебного сообщения с меткой назначения.
     *
     * @param messagePurpose назначение: server-confirm, client-confirm либо acknowledgement
     * @param payload содержимое служебного сообщения
     * @return 32-байтовый тег
     */
    public byte[] authenticateControlMessage(String messagePurpose, byte[] payload) {
        return createAuthenticationMac(messagePurpose).doFinal(payload);
    }

    /**
     * Подготавливает HMAC с отдельной меткой и хешем открытого обмена.
     *
     * @param purpose назначение сообщения
     * @return независимое состояние HMAC
     */
    private Mac createAuthenticationMac(String purpose) {
        requireOpen();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(authenticationKey, "HmacSHA256"));
            mac.update(("securetransfer/file/v1/" + purpose + "\0").getBytes(StandardCharsets.UTF_8));
            mac.update(exchangeTranscriptHash);
            return mac;
        } catch (GeneralSecurityException unavailableMac) {
            throw new IllegalStateException("Не удалось подключить HMAC-SHA256", unavailableMac);
        }
    }

    /**
     * Кодирует открытые сообщения и получает два ключа из одного общего секрета.
     *
     * @param sharedSecret общий секрет после проверок MQV
     * @param serverData открытые данные сервера
     * @param clientData открытые данные клиента
     * @return ключи и контекст одного сеанса
     * @throws IOException если открытые данные не удалось закодировать
     */
    private static FileTransferSession createSession(
            BigInteger sharedSecret, PublicExchangeData serverData, PublicExchangeData clientData
    ) throws IOException {
        ByteArrayOutputStream transcriptBytes = new ByteArrayOutputStream();
        try (DataOutputStream transcriptOutput = new DataOutputStream(transcriptBytes)) {
            KeyExchangeTransport.sendMessage(transcriptOutput, new KeyExchangeMessage(ProtocolType.MQV, serverData));
            KeyExchangeTransport.sendMessage(transcriptOutput, new KeyExchangeMessage(ProtocolType.MQV, clientData));
        }
        Sha3Hasher hashFunction = new Sha3Hasher();
        byte[] transcriptHash = hashFunction.calculateHash(transcriptBytes.toByteArray());
        MqvKeyDerivation derivation = new MqvKeyDerivation(serverData.groupParameters(), hashFunction);
        byte[] cipherKey = derivation.deriveKey(sharedSecret, RgzSettings.FILE_CIPHER.createCipher().getKeyLengthBytes());
        byte[] macKey = derivation.deriveAuthenticationKey(sharedSecret, transcriptHash);
        return new FileTransferSession(cipherKey, macKey, transcriptHash);
    }

    /**
     * Исключает переключение протокола РГЗ с MQV на другой обмен.
     *
     * @param message принятое сообщение общего транспорта
     * @throws IOException если отправитель указал другой алгоритм
     */
    private static void requireMqv(KeyExchangeMessage message) throws IOException {
        if (message.protocolType() != ProtocolType.MQV) {
            throw new IOException("Для передачи файлов РГЗ требуется MQV");
        }
    }

    /** Проверяет, что ключи сеанса ещё не закрыты. */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Сеанс передачи уже закрыт");
        }
    }

    /** Перезаписывает принадлежащие сеансу массивы ключей после завершения работы. */
    @Override
    public void close() {
        Arrays.fill(encryptionKey, (byte) 0);
        Arrays.fill(authenticationKey, (byte) 0);
        Arrays.fill(exchangeTranscriptHash, (byte) 0);
        closed = true;
    }
}
