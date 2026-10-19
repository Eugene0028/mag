package securetransfer.lab1.network;

import securetransfer.common.network.KeyExchangeMessage;
import securetransfer.common.network.KeyExchangeTransport;
import securetransfer.common.protocol.KeyAgreementProtocol;
import securetransfer.common.protocol.KeyAgreementProtocolFactory;
import securetransfer.common.protocol.ProtocolType;
import securetransfer.lab1.config.Lab1Settings;

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
                ? commandLineArguments[0] : Lab1Settings.DEFAULT_SERVER_HOST;
        int serverPort = commandLineArguments.length == 2
                ? Integer.parseInt(commandLineArguments[1]) : Lab1Settings.DEFAULT_SERVER_PORT;
        if (serverHost.isBlank()) {
            throw new IllegalArgumentException("Адрес сервера не должен быть пустым");
        }
        if (serverPort < 1 || serverPort > 65535) {
            throw new IllegalArgumentException("Порт должен находиться в диапазоне от 1 до 65535");
        }

        try (Socket serverConnection = new Socket()) {
            System.out.printf("Подключение к серверу %s:%d%n", serverHost, serverPort);
            serverConnection.connect(
                    new InetSocketAddress(serverHost, serverPort), Lab1Settings.CONNECT_TIMEOUT_MILLISECONDS
            );
            serverConnection.setSoTimeout(Lab1Settings.READ_TIMEOUT_MILLISECONDS);

            try (DataInputStream networkInput = new DataInputStream(serverConnection.getInputStream());
                 DataOutputStream networkOutput = new DataOutputStream(serverConnection.getOutputStream())) {
                KeyExchangeMessage serverMessage = KeyExchangeTransport.receiveMessage(networkInput);
                ProtocolType selectedProtocol = serverMessage.protocolType();
                System.out.println("Алгоритм сервера: " + selectedProtocol.getDisplayName());
                KeyAgreementProtocolFactory protocolFactory = new KeyAgreementProtocolFactory(
                        Lab1Settings.DEMONSTRATION_GROUP_SETTINGS, Lab1Settings.DATA_PATHS
                );
                KeyAgreementProtocol clientProtocol = protocolFactory.createClientProtocol(
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
