package securetransfer;

import securetransfer.console.ConsoleInput;
import securetransfer.network.KeyAgreementClientMain;
import securetransfer.network.KeyAgreementServerMain;

import java.io.IOException;
import java.net.BindException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Общее меню запуска учебных примеров.
 * Классы отдельных примеров по-прежнему можно запускать непосредственно в IDEA.
 */
public final class LauncherMain {
    /** Запрещает создание экземпляров класса запуска. */
    private LauncherMain() {
    }

    /**
     * Показывает меню либо запускает режим, переданный первым аргументом.
     *
     * @param commandLineArguments [dh-full|dh-subgroup|mqv|server|client|benchmark] [аргументы режима]
     */
    public static void main(String[] commandLineArguments) {
        try {
            if (commandLineArguments.length == 0) {
                showMenu();
            } else {
                runSelectedMode(commandLineArguments[0],
                        Arrays.copyOfRange(commandLineArguments, 1, commandLineArguments.length));
            }
        } catch (BindException portUnavailable) {
            System.err.println("Порт занят. Закройте предыдущий сервер или выберите другой порт.");
            System.exit(1);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException failure) {
            System.err.println("Не удалось выполнить выбранный режим: " + failure.getMessage());
            System.exit(1);
        }
    }

    /**
     * Предлагает выбрать пример и при сетевом запуске запрашивает адрес и порт.
     *
     * @throws IOException если ввод или файлы недоступны либо произошла ошибка сети
     * @throws GeneralSecurityException если подпись справочника MQV неверна
     */
    private static void showMenu() throws IOException, GeneralSecurityException {
        System.out.println("SecureTransfer — лабораторная работа 1");
        System.out.println("1. Локальный DH в полной группе");
        System.out.println("2. Локальный DH в подгруппе");
        System.out.println("3. Локальный MQV");
        System.out.println("4. Сетевой сервер");
        System.out.println("5. Сетевой клиент");
        System.out.println("6. Замеры этапов DH и MQV в процессорных циклах");
        System.out.println("0. Выход");

        while (true) {
            String selectedItem = ConsoleInput.readLine("Ваш выбор: ");
            switch (selectedItem) {
                case "1" -> runSelectedMode("dh-full", new String[0]);
                case "2" -> runSelectedMode("dh-subgroup", new String[0]);
                case "3" -> runSelectedMode("mqv", new String[0]);
                case "4" -> {
                    String serverPort = ConsoleInput.readLine("Порт сервера [5055]: ");
                    runSelectedMode("server", new String[]{serverPort.isEmpty() ? "5055" : serverPort});
                }
                case "5" -> {
                    String serverAddress = ConsoleInput.readLine("Адрес сервера [127.0.0.1]: ");
                    String serverPort = ConsoleInput.readLine("Порт сервера [5055]: ");
                    runSelectedMode("client", new String[]{
                            serverAddress.isEmpty() ? "127.0.0.1" : serverAddress,
                            serverPort.isEmpty() ? "5055" : serverPort
                    });
                }
                case "6" -> runSelectedMode("benchmark", new String[0]);
                case "0" -> {
                    return;
                }
                default -> {
                    System.out.println("Введите номер от 0 до 6.");
                    continue;
                }
            }
            return;
        }
    }

    /**
     * Передаёт управление существующему main выбранного примера.
     *
     * @param selectedMode текстовый идентификатор режима
     * @param modeArguments аргументы конкретного примера
     * @throws IOException если файлы или сеть недоступны
     * @throws GeneralSecurityException если проверка подписи MQV не удалась
     */
    private static void runSelectedMode(String selectedMode, String[] modeArguments)
            throws IOException, GeneralSecurityException {
        switch (selectedMode) {
            case "dh-full" -> FullGroupDhMain.main(modeArguments);
            case "dh-subgroup" -> SubgroupDhMain.main(modeArguments);
            case "mqv" -> MqvMain.main(modeArguments);
            case "server" -> KeyAgreementServerMain.main(modeArguments);
            case "client" -> KeyAgreementClientMain.main(modeArguments);
            case "benchmark", "counter" -> BenchmarkMain.main(modeArguments);
            default -> throw new IllegalArgumentException(
                    "Режимы запуска: dh-full, dh-subgroup, mqv, server, client, benchmark."
            );
        }
    }
}
