package securetransfer.common.network;

import securetransfer.common.core.GroupParameters;
import securetransfer.common.protocol.DhPublicExchangeData;
import securetransfer.common.protocol.MqvPublicExchangeData;
import securetransfer.common.protocol.ProtocolType;
import securetransfer.common.protocol.PublicExchangeData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;

/**
 * Передача открытых параметров и ключей через потоки TCP-соединения.
 *
 * <p>Числа передаются десятичными строками. writeUTF записывает длину строки,
 * поэтому получатель может прочитать каждое число целиком независимо от границ TCP-пакетов.
 * Секретные ключи и общий секрет через этот класс не передаются.</p>
 */
public final class KeyExchangeTransport {
    /**
     * Идентификатор общего формата передачи открытых данных.
     */
    private static final String PROTOCOL_HEADER = "SECURETRANSFER_KEY_EXCHANGE_V3";

    /**
     * Ограничение длины одного числа, допускающее параметры размером до 4096 бит.
     */
    private static final int MAXIMUM_NUMBER_TEXT_LENGTH = 1300;

    /**
     * Служебный конструктор класса статических операций передачи.
     */
    private KeyExchangeTransport() {
    }

    /**
     * Передаёт выбранный алгоритм, параметры группы и открытые ключи участника.
     *
     * @param networkOutput поток отправки данных собеседнику
     * @param message сообщение с выбранным алгоритмом и открытыми данными
     * @throws IOException если тип данных неизвестен, числа слишком велики или отправка не удалась
     */
    public static void sendMessage(
            DataOutputStream networkOutput,
            KeyExchangeMessage message
    ) throws IOException {
        networkOutput.writeUTF(PROTOCOL_HEADER);
        networkOutput.writeUTF(message.protocolType().name());
        PublicExchangeData publicExchangeData = message.publicExchangeData();
        writeGroupParameters(networkOutput, publicExchangeData.groupParameters());

        if (publicExchangeData instanceof DhPublicExchangeData dhData) {
            writeBigInteger(networkOutput, dhData.publicKey());
        } else if (publicExchangeData instanceof MqvPublicExchangeData mqvData) {
            writeBigInteger(networkOutput, mqvData.longTermPublicKey());
            writeBigInteger(networkOutput, mqvData.ephemeralPublicKey());
        } else {
            throw new IOException("Неизвестный тип открытых данных участника");
        }

        networkOutput.flush();
    }

    /**
     * Читает выбранный алгоритм и соответствующие ему открытые данные.
     *
     * @param networkInput поток получения данных от собеседника
     * @return сообщение; математические проверки выполняет реализация выбранного протокола
     * @throws IOException если формат неизвестен, числа неверны или чтение не удалось
     */
    public static KeyExchangeMessage receiveMessage(DataInputStream networkInput) throws IOException {
        if (!PROTOCOL_HEADER.equals(networkInput.readUTF())) {
            throw new IOException("Несовместимый формат сообщений. Перезапустите сервер и клиент с текущим кодом.");
        }

        ProtocolType protocolType;
        String receivedProtocolName = networkInput.readUTF();
        try {
            protocolType = ProtocolType.valueOf(receivedProtocolName);
        } catch (IllegalArgumentException unknownProtocolException) {
            throw new IOException("Неизвестный алгоритм обмена: " + receivedProtocolName, unknownProtocolException);
        }

        GroupParameters groupParameters = readGroupParameters(networkInput);
        PublicExchangeData publicExchangeData;
        if (protocolType == ProtocolType.MQV) {
            publicExchangeData = new MqvPublicExchangeData(
                    groupParameters,
                    readBigInteger(networkInput),
                    readBigInteger(networkInput)
            );
        } else {
            publicExchangeData = new DhPublicExchangeData(groupParameters, readBigInteger(networkInput));
        }
        return new KeyExchangeMessage(protocolType, publicExchangeData);
    }

    /**
     * Читает параметры p, q, g в порядке их записи.
     *
     * @param networkInput поток получения
     * @return открытые параметры, требующие математической проверки
     * @throws IOException если формат чисел неверен или чтение не удалось
     */
    private static GroupParameters readGroupParameters(DataInputStream networkInput) throws IOException {
        return new GroupParameters(
                readBigInteger(networkInput),
                readBigInteger(networkInput),
                readBigInteger(networkInput)
        );
    }

    /**
     * Записывает параметры p, q, g в указанном порядке.
     *
     * @param networkOutput поток отправки
     * @param groupParameters открытые параметры группы
     * @throws IOException если числа слишком велики или запись не удалась
     */
    private static void writeGroupParameters(
            DataOutputStream networkOutput,
            GroupParameters groupParameters
    ) throws IOException {
        writeBigInteger(networkOutput, groupParameters.primeModulus());
        writeBigInteger(networkOutput, groupParameters.subgroupOrder());
        writeBigInteger(networkOutput, groupParameters.generator());
    }

    /**
     * Записывает одно число строкой с проверкой допустимой длины.
     *
     * @param networkOutput поток отправки
     * @param transmittedNumber передаваемое открытое число
     * @throws IOException если число слишком велико или запись не удалась
     */
    private static void writeBigInteger(
            DataOutputStream networkOutput,
            BigInteger transmittedNumber
    ) throws IOException {
        String numberText = transmittedNumber.toString();
        if (numberText.length() > MAXIMUM_NUMBER_TEXT_LENGTH) {
            throw new IOException("Передаваемое число превышает допустимую длину");
        }
        networkOutput.writeUTF(numberText);
    }

    /**
     * Читает строку ограниченной длины и преобразует её в BigInteger.
     *
     * @param networkInput поток получения
     * @return прочитанное число
     * @throws IOException если строка пуста, слишком велика, не является числом или чтение не удалось
     */
    private static BigInteger readBigInteger(DataInputStream networkInput) throws IOException {
        String numberText = networkInput.readUTF();
        if (numberText.isEmpty() || numberText.length() > MAXIMUM_NUMBER_TEXT_LENGTH) {
            throw new IOException("Получено число недопустимой длины");
        }

        try {
            return new BigInteger(numberText);
        } catch (NumberFormatException invalidNumberException) {
            throw new IOException("Полученные данные не являются целым десятичным числом", invalidNumberException);
        }
    }
}
