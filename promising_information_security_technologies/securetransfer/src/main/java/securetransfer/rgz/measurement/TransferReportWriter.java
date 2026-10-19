package securetransfer.rgz.measurement;

import securetransfer.rgz.config.RgzSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Сохранение фаз передачи в CSV и HTML без ключей и содержимого файлов. */
public final class TransferReportWriter {
    /** Каталог одного запуска с исходными наблюдениями. */
    private final Path reportDirectory;

    /** Явное описание топологии: loopback или ручной запуск с указанным адресом. */
    private final String experimentDescription;

    /** Завершённые наблюдения; неудачные передачи не превращаются в успешные строки. */
    private final List<Sample> samples = new ArrayList<>();

    /**
     * Создаёт новый каталог, сохраняет условия и заголовок CSV.
     * @param experimentDescription фактический сценарий запуска
     * @throws IOException если каталог недоступен
     */
    public TransferReportWriter(String experimentDescription) throws IOException {
        this.experimentDescription = experimentDescription;
        String runName = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(OffsetDateTime.now())
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        reportDirectory = RgzSettings.DATA_PATHS.reportsDirectory().resolve("file-transfer")
                .resolve(runName).toAbsolutePath().normalize();
        Files.createDirectories(reportDirectory);
        StringBuilder header = new StringBuilder("timestamp,role,peer,file_name,size_bytes,repetition,warmup,verification");
        for (TransferPhase phase : TransferPhase.values()) {
            header.append(',').append(phase.name().toLowerCase(Locale.ROOT)).append("_ns");
        }
        Files.writeString(reportDirectory.resolve("measurements.csv"), header.append('\n'), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        Files.writeString(reportDirectory.resolve("environment.txt"), environmentDescription(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }

    /**
     * Записывает завершённую передачу и обновляет HTML с медианами без прогрева.
     * @param measurement замеры одной стороны
     * @param role sender или receiver
     * @param peer адрес собеседника
     * @param repetition номер повтора
     * @param warmup исключать ли наблюдение из статистики
     * @param verification фактически выполненная проверка
     * @throws IOException если сохранить результат не удалось
     */
    public synchronized void append(TransferMeasurement measurement, String role, String peer,
                                    int repetition, boolean warmup, String verification) throws IOException {
        Sample sample = new Sample(measurement, role, peer, repetition, warmup, verification);
        StringBuilder row = new StringBuilder(quote(OffsetDateTime.now().toString()));
        row.append(',').append(quote(role)).append(',').append(quote(peer)).append(',')
                .append(quote(measurement.fileName())).append(',').append(measurement.fileSizeBytes())
                .append(',').append(repetition).append(',').append(warmup).append(',').append(quote(verification));
        for (TransferPhase phase : TransferPhase.values()) {
            row.append(',').append(measurement.nanoseconds(phase));
        }
        Files.writeString(reportDirectory.resolve("measurements.csv"), row.append('\n'), StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        samples.add(sample);
        saveHtml();
    }

    /** @return каталог CSV, HTML и описания окружения */
    public Path reportDirectory() {
        return reportDirectory;
    }

    /** @return фактические настройки и сведения о JVM, без сетевых или секретных ключей */
    private String environmentDescription() {
        return experimentDescription + "\nДата: " + OffsetDateTime.now()
                + "\nОС: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + "\nАрхитектура: " + System.getProperty("os.arch")
                + "\nJVM: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version")
                + "; " + System.getProperty("java.vendor")
                + "\nДоступно процессоров: " + Runtime.getRuntime().availableProcessors()
                + "\nMQV p/q: " + RgzSettings.KEY_AGREEMENT_GROUP_SETTINGS.modulusBitLength() + "/"
                + RgzSettings.KEY_AGREEMENT_GROUP_SETTINGS.subgroupOrderBitLength()
                + "\nШифр: " + RgzSettings.FILE_CIPHER + "; ключ: 128 бит; SHA3-256; HMAC-SHA256"
                + "\nБуфер: " + RgzSettings.FILE_BUFFER_LENGTH_BYTES + " байт"
                + "\nТаймер: System.nanoTime, прошедшее время, исходные значения в наносекундах\n";
    }

    /**
     * Строит таблицу по тем же наблюдениям, что записаны в CSV.
     * @throws IOException при ошибке чтения CSV или записи HTML
     */
    private void saveHtml() throws IOException {
        StringBuilder table = new StringBuilder();
        List<Sample> measured = samples.stream().filter(sample -> !sample.warmup()).toList();
        for (String role : List.of("sender", "receiver")) {
            long[] sizes = measured.stream().filter(sample -> sample.role().equals(role))
                    .mapToLong(sample -> sample.measurement().fileSizeBytes()).distinct().sorted().toArray();
            for (long size : sizes) {
                List<Sample> group = measured.stream().filter(sample -> sample.role().equals(role)
                        && sample.measurement().fileSizeBytes() == size).toList();
                table.append("<tr><td>").append(role).append("</td><td>").append(size)
                        .append("</td><td>").append(group.size()).append("</td>");
                for (TransferPhase phase : TransferPhase.values()) {
                    double[] times = group.stream().mapToDouble(sample -> sample.measurement().nanoseconds(phase)
                            / 1_000_000.0).sorted().toArray();
                    double median = (times[(times.length - 1) / 2] + times[times.length / 2]) / 2;
                    table.append("<td>").append(String.format(Locale.ROOT, "%.4f", median)).append("</td>");
                }
                table.append("</tr>");
            }
        }
        String headers = Arrays.stream(TransferPhase.values()).map(phase -> "<th>" + phase + ", мс</th>")
                .reduce("", String::concat);
        String conclusion = "";
        Sample largest = measured.stream().filter(sample -> sample.role().equals("sender"))
                .max(Comparator.comparingLong(sample -> sample.measurement().fileSizeBytes())).orElse(null);
        if (largest != null) {
            long largestSize = largest.measurement().fileSizeBytes();
            List<Sample> largeFiles = measured.stream().filter(sample -> sample.role().equals("sender")
                    && sample.measurement().fileSizeBytes() == largestSize).toList();
            double[] intervals = largeFiles.stream().mapToDouble(sample -> sample.measurement()
                    .nanoseconds(TransferPhase.TRANSFER_ELAPSED) / 1_000_000_000.0).sorted().toArray();
            double medianSeconds = (intervals[(intervals.length - 1) / 2] + intervals[intervals.length / 2]) / 2;
            conclusion = String.format(Locale.ROOT,
                    "<p>Для файла %d байт медиана полного интервала отправителя %.4f с, "
                            + "полезная скорость %.2f МиБ/с. Она включает обработку файла и подтверждение "
                            + "и не является пропускной способностью физического канала.</p>",
                    largestSize, medianSeconds, largestSize / 1048576.0 / medianSeconds);
        }
        String html = """
                <!doctype html><html lang="ru"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Фазы передачи файлов SecureTransfer</title>
                <style>body{font:16px/1.5 system-ui;max-width:1500px;margin:32px auto;padding:0 20px;color:#18232e}
                table{border-collapse:collapse;font-size:13px;width:100%}td,th{border:1px solid #ccd2d8;padding:8px;text-align:right}
                th{background:#eef1f4}pre{white-space:pre-wrap;overflow-wrap:anywhere}.scroll{overflow:auto}h1,h2{line-height:1.2}</style>
                <h1>Фазы передачи файлов</h1><pre>ENVIRONMENT</pre>
                <p>Таблица содержит медианы в миллисекундах. Прогрев исключён. Нулём обозначены отсутствующие
                на этой стороне фазы. sender шифрует, receiver расшифровывает. Все фазы измерены по прошедшему времени.</p>
                <div class="scroll"><table><thead><tr><th>Сторона</th><th>Байты</th><th>Повторы</th>HEADERS</tr></thead>
                <tbody>ROWS</tbody></table></div>
                <h2>Границы измерений</h2>
                <p>PREPARATION — подготовка серверного MQV до accept, включая проверки параметров и файлов.
                CONNECTION — TCP connect отправителя. KEY_ESTABLISHMENT — обмен MQV, проверки, SHA3 и подтверждения
                после подключения, без предварительной подготовки получателя. CIPHER_SETUP — создание шифра и установка ключа и IV.
                CIPHER — только вызовы processBytes. AUTHENTICATION — обновления HMAC в цикле обработки.
                DISK_IO — чтение или запись порций файла без открытия и force.
                NETWORK_IO — сумма интервалов вызовов сетевых потоков, flush, окончания потока и подтверждения.
                Буферизация и ожидание собеседника входят в эти интервалы; это не чистое время прохождения пакетов.
                TRANSFER_ELAPSED — весь файловый этап до подтверждения, включая IV, шифр, HMAC, диск и сеть.</p>
                <p>Вложенные интервалы и наблюдения разных сторон перекрываются. Их нельзя складывать.
                Часы компьютеров не синхронизируются: каждый измеряет длительности своим nanoTime.
                Для локальной серии обе стороны работают в одной JVM на одном компьютере через TCP loopback.
                Эти данные не заменяют опыт между двумя компьютерами.</p>
                <h2>Выводы</h2>CONCLUSION
                <p>MQV и проверки доверия создают расходы на каждый файл; для коротких файлов они могут преобладать.
                С ростом размера увеличивается роль потоковой обработки, HMAC и ввода-вывода.
                Выводы относятся только к указанному окружению. Накладные расходы nanoTime не вычитаются.</p>
                <p>Подтверждённых наблюдений: COUNT; прогревочных: WARMUPS.
                Проверки успешных строк указаны в CSV; отсутствие строки не означает успешную передачу.</p>
                <p><a href="measurements.csv">Исходный CSV</a> · <a href="environment.txt">Окружение</a></p>
                <details><summary>Содержимое CSV</summary><pre>{{RAW_CSV}}</pre></details></html>
                """;
        html = html.replace("ENVIRONMENT", escape(environmentDescription())).replace("HEADERS", headers)
                .replace("ROWS", table).replace("CONCLUSION", conclusion)
                .replace("COUNT", Integer.toString(measured.size()))
                .replace("WARMUPS", Long.toString(samples.stream().filter(Sample::warmup).count()))
                .replace("{{RAW_CSV}}", escape(Files.readString(reportDirectory.resolve("measurements.csv"))));
        Files.writeString(reportDirectory.resolve("report.html"), html, StandardCharsets.UTF_8);
    }

    /** @param value текст ячейки @return корректное поле CSV RFC 4180 */
    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** @param value текст @return безопасное содержимое HTML */
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Одно наблюдение с признаками прогрева и выполненной проверки. */
    private record Sample(TransferMeasurement measurement, String role, String peer,
                          int repetition, boolean warmup, String verification) {
    }
}
