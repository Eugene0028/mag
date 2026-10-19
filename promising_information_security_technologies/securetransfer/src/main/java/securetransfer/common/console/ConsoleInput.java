package securetransfer.common.console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Общий поток консольного ввода для меню запуска и выбора алгоритма на сервере. */
public final class ConsoleInput {
    /** Единственный буфер ввода: вложенные меню не теряют уже прочитанные строки. */
    private static final BufferedReader INPUT = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8)
    );

    /** Запрещает создание экземпляров служебного класса. */
    private ConsoleInput() {
    }

    /**
     * Показывает приглашение и читает одну строку, не закрывая System.in.
     *
     * @param prompt приглашение пользователю
     * @return строка без окружающих пробелов
     * @throws IOException если ввод закончился или недоступен
     */
    public static String readLine(String prompt) throws IOException {
        System.out.print(prompt);
        System.out.flush();
        String enteredLine = INPUT.readLine();
        if (enteredLine == null) {
            throw new IOException("Консольный ввод завершён.");
        }
        return enteredLine.strip();
    }
}
