package securetransfer.lab1.network;

import securetransfer.common.console.ConsoleInput;
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
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;

/**
 * Общий TCP-сервер для DH в полной группе, DH в подгруппе и MQV.
 *
 * <p>Предлагает выбрать алгоритм, подготавливает параметры, обслуживает одного клиента и завершает работу.
 * Для запуска в IDEA достаточно запустить этот класс перед клиентом.</p>
 */
public class KeyAgreementServerMain {
    /**
     * Выбирает алгоритм через меню, принимает подключение и вычисляет свой общий секрет.
     *
     * @param commandLineArguments необязательный аргумент: порт от 1 до 65535; по умолчанию 5055
     * @throws IOException если параметры недоступны или произошла ошибка сети
     * @throws GeneralSecurityException если ключ центра или подпись справочника MQV неверны
     * @throws IllegalArgumentException если порт, параметры подгруппы или открытый ключ неверны
     * @throws IllegalStateException если MQV требует повторить обмен с новыми временными парами
     */
    public static void main(String[] commandLineArguments) throws IOException, GeneralSecurityException {
        if (commandLineArguments.length > 1) {
            throw new IllegalArgumentException("Аргументы сервера: [порт]");
        }
        int serverPort = commandLineArguments.length == 1
                ? Integer.parseInt(commandLineArguments[0]) : Lab1Settings.DEFAULT_SERVER_PORT;
        if (serverPort < 1 || serverPort > 65535) {
            throw new IllegalArgumentException("Порт должен находиться в диапазоне от 1 до 65535");
        }

        ProtocolType selectedProtocol = chooseProtocol();
        System.out.println("Выбран алгоритм: " + selectedProtocol.getDisplayName());
        KeyAgreementProtocolFactory protocolFactory = new KeyAgreementProtocolFactory(
                Lab1Settings.DEMONSTRATION_GROUP_SETTINGS, Lab1Settings.DATA_PATHS
        );
        KeyAgreementProtocol serverProtocol = protocolFactory.createServerProtocol(selectedProtocol);

        try (ServerSocket listeningSocket = new ServerSocket(serverPort)) {
            System.out.println("Сервер ожидает подключение на порту " + serverPort);

            try (Socket clientConnection = listeningSocket.accept();
                 DataInputStream networkInput = new DataInputStream(clientConnection.getInputStream());
                 DataOutputStream networkOutput = new DataOutputStream(clientConnection.getOutputStream())) {
                clientConnection.setSoTimeout(Lab1Settings.READ_TIMEOUT_MILLISECONDS);
                System.out.println("Клиент подключился: " + clientConnection.getRemoteSocketAddress());

                KeyExchangeTransport.sendMessage(networkOutput,
                        new KeyExchangeMessage(selectedProtocol, serverProtocol.getPublicExchangeData()));
                System.out.println("Алгоритм, параметры и открытые данные сервера отправлены.");

                KeyExchangeMessage clientMessage = KeyExchangeTransport.receiveMessage(networkInput);
                if (clientMessage.protocolType() != selectedProtocol) {
                    throw new IOException("Клиент ответил с другим алгоритмом обмена");
                }
                BigInteger serverSharedSecret = serverProtocol.calculateSharedSecret(clientMessage.publicExchangeData());

                // Секрет показывается локально для сравнения двух учебных запусков.
                System.out.println("Общий секрет на сервере: " + serverSharedSecret);
            }
        }
    }

    /**
     * Показывает меню и читает выбор пользователя, повторяя ввод при неверном номере.
     *
     * @return выбранный алгоритм обмена
     * @throws IOException если консоль недоступна или ввод закончился до выбора
     */
    private static ProtocolType chooseProtocol() throws IOException {
        System.out.println("Выберите алгоритм:");
        System.out.println("1. " + ProtocolType.DH_FULL_GROUP.getDisplayName());
        System.out.println("2. " + ProtocolType.DH_SUBGROUP.getDisplayName());
        System.out.println("3. " + ProtocolType.MQV.getDisplayName());

        while (true) {
            String userChoice = ConsoleInput.readLine("Ваш выбор: ");
            switch (userChoice.strip()) {
                case "1":
                    return ProtocolType.DH_FULL_GROUP;
                case "2":
                    return ProtocolType.DH_SUBGROUP;
                case "3":
                    return ProtocolType.MQV;
                default:
                    System.out.println("Введите 1, 2 или 3.");
            }
        }
    }
}
