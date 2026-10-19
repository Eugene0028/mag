package securetransfer.network;

import securetransfer.protocol.KeyAgreementProtocol;
import securetransfer.protocol.KeyAgreementProtocolFactory;
import securetransfer.protocol.ProtocolType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;

/**
 * Общий TCP-клиент для DH в полной группе, DH в подгруппе и MQV.
 *
 * <p>При запуске без аргументов подключается к серверу на том же компьютере.
 * Для другого компьютера передайте адрес сервера первым аргументом.
 * Алгоритм получает от сервера и выбирает автоматически через фабрику.</p>
 */
public class KeyAgreementClientMain {
    /**
     * Адрес сервера по умолчанию: тот же компьютер, на котором запущен клиент.
     *
     * <p>Адрес используется только для локального учебного запуска.</p>
     */
    private static final String DEFAULT_SERVER_HOST = "127.0.0.1";

    /**
     * Порт, соответствующий запуску сервера без аргументов.
     */
    private static final int DEFAULT_SERVER_PORT = 5055;

    /**
     * Максимальное ожидание установления соединения с сервером.
     */
    private static final int CONNECT_TIMEOUT_MILLISECONDS = 5_000;

    /**
     * Максимальное ожидание очередного чтения из установленного соединения.
     */
    private static final int READ_TIMEOUT_MILLISECONDS = 30_000;

    /**
     * Получает выбранный сервером алгоритм и открытые данные, подготавливает свою сторону обмена.
     *
     * @param commandLineArguments необязательные аргументы: [адрес сервера] [порт]; по умолчанию 127.0.0.1 5055
     * @throws IOException если подключение, чтение или отправка не удались
     * @throws GeneralSecurityException если ключ центра или подпись справочника MQV неверны
     * @throws IllegalArgumentException если адрес, порт, параметры подгруппы или открытый ключ неверны
     * @throws IllegalStateException если MQV требует повторить обмен с новыми временными парами
     */
    public static void main(String[] commandLineArguments) throws IOException, GeneralSecurityException {
        if (commandLineArguments.length > 2) {
            throw new IllegalArgumentException("Аргументы клиента: [адрес сервера] [порт]");
        }
        String serverHost = commandLineArguments.length >= 1
                ? commandLineArguments[0] : DEFAULT_SERVER_HOST;
        int serverPort = commandLineArguments.length == 2
                ? Integer.parseInt(commandLineArguments[1]) : DEFAULT_SERVER_PORT;
        if (serverHost.isBlank()) {
            throw new IllegalArgumentException("Адрес сервера не должен быть пустым");
        }
        if (serverPort < 1 || serverPort > 65535) {
            throw new IllegalArgumentException("Порт должен находиться в диапазоне от 1 до 65535");
        }

        try (Socket serverConnection = new Socket()) {
            System.out.printf("Подключение к серверу %s:%d%n", serverHost, serverPort);
            serverConnection.connect(new InetSocketAddress(serverHost, serverPort), CONNECT_TIMEOUT_MILLISECONDS);
            serverConnection.setSoTimeout(READ_TIMEOUT_MILLISECONDS);

            try (DataInputStream networkInput = new DataInputStream(serverConnection.getInputStream());
                 DataOutputStream networkOutput = new DataOutputStream(serverConnection.getOutputStream())) {
                KeyExchangeMessage serverMessage = KeyExchangeTransport.receiveMessage(networkInput);
                ProtocolType selectedProtocol = serverMessage.protocolType();
                System.out.println("Алгоритм сервера: " + selectedProtocol.getDisplayName());
                KeyAgreementProtocol clientProtocol = KeyAgreementProtocolFactory.createClientProtocol(
                        selectedProtocol, serverMessage.publicExchangeData().groupParameters()
                );
                BigInteger clientSharedSecret = clientProtocol.calculateSharedSecret(serverMessage.publicExchangeData());

                KeyExchangeTransport.sendMessage(networkOutput,
                        new KeyExchangeMessage(selectedProtocol, clientProtocol.getPublicExchangeData()));
                System.out.println("Открытые данные клиента отправлены.");

                // Секрет показывается локально для сравнения двух учебных запусков.
                System.out.println("Общий секрет на клиенте: " + clientSharedSecret);
            }
        }
    }
}
