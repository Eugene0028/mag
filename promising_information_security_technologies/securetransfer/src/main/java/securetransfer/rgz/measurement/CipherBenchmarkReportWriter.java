package securetransfer.rgz.measurement;

import securetransfer.common.measurement.BenchmarkSettings;
import securetransfer.common.measurement.StageMeasurement;
import securetransfer.common.storage.BenchmarkCsvFileStorage;
import securetransfer.rgz.config.RgzSettings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Сохранение исходных циклов в CSV и построение HTML-сравнения по сохранённым данным. */
public final class CipherBenchmarkReportWriter {
    /** Формат имени каталога отдельного запуска. */
    private static final DateTimeFormatter DIRECTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Настройки прогрева и повторений, выводимые в отчёте. */
    private final BenchmarkSettings measurementSettings;

    /** Размер порции, по которому циклы на операцию переводятся в циклы на байт. */
    private final int keyStreamBlockLengthBytes;

    /** Описание использованного системного счётчика. */
    private final String counterDescription;

    /**
     * Сохраняет параметры проведённого эксперимента.
     *
     * @param measurementSettings прогрев и повторения
     * @param keyStreamBlockLengthBytes размер одной порции потока
     * @param counterDescription источник процессорных циклов
     */
    public CipherBenchmarkReportWriter(
            BenchmarkSettings measurementSettings, int keyStreamBlockLengthBytes, String counterDescription
    ) {
        this.measurementSettings = measurementSettings;
        this.keyStreamBlockLengthBytes = keyStreamBlockLengthBytes;
        this.counterDescription = counterDescription;
    }

    /**
     * Создаёт каталог запуска, сохраняет CSV и формирует таблицу с выводом по этому CSV.
     *
     * @param measurements завершённые этапы четырёх шифров
     * @param counterOverhead отдельные измерения пустой операции
     * @return абсолютный путь к HTML-отчёту
     * @throws IOException если записать результаты или прочитать HTML-шаблон не удалось
     */
    public Path saveReport(List<StageMeasurement> measurements, StageMeasurement counterOverhead) throws IOException {
        LocalDateTime reportTime = LocalDateTime.now();
        String directoryName = DIRECTORY_TIME_FORMAT.format(reportTime) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path reportDirectory = RgzSettings.DATA_PATHS.reportsDirectory().resolve("stream-ciphers")
                .resolve(directoryName).toAbsolutePath().normalize();
        Files.createDirectories(reportDirectory);
        Path csvPath = reportDirectory.resolve("measurements.csv");
        BenchmarkCsvFileStorage.saveMeasurements(csvPath, measurements, counterOverhead);
        BenchmarkCsvFileStorage.StoredMeasurements stored = BenchmarkCsvFileStorage.loadMeasurements(csvPath);

        String template;
        try (InputStream templateStream = CipherBenchmarkReportWriter.class
                .getResourceAsStream("/rgz/cipher-benchmark-report.html")) {
            if (templateStream == null) {
                throw new IOException("HTML-шаблон РГЗ не найден в ресурсах проекта");
            }
            template = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String html = template
                .replace("{{DATE}}", escapeHtml(reportTime.toString()))
                .replace("{{ENVIRONMENT}}", buildEnvironmentDescription())
                .replace("{{ROWS}}", buildMeasurementRows(stored.stages()))
                .replace("{{CONCLUSION}}", buildConclusion(stored.stages()))
                .replace("{{OVERHEAD}}", formatNumber(stored.overhead().calculateMedianCyclesPerOperation()))
                .replace("{{RAW_CSV}}", escapeHtml(Files.readString(csvPath, StandardCharsets.UTF_8)));
        Path htmlPath = reportDirectory.resolve("report.html");
        Files.writeString(htmlPath, html, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return htmlPath;
    }

    /**
     * Описывает настройки, ОС, JVM и источник циклов без обращения к сети.
     *
     * @return экранированный фрагмент HTML со списком параметров
     */
    private String buildEnvironmentDescription() {
        return "<ul><li>Счётчик: " + escapeHtml(counterDescription)
                + "</li><li>ОС: " + escapeHtml(System.getProperty("os.name") + " "
                + System.getProperty("os.version") + "; " + System.getProperty("os.arch"))
                + "</li><li>Java: " + escapeHtml(System.getProperty("java.version") + "; "
                + System.getProperty("java.vendor") + "; " + System.getProperty("java.vm.name"))
                + "</li><li>Ключи: 128 бит; IV: 64 бита у Rabbit и Salsa20, 128 бит у HC-128 и Sosemanuk"
                + "</li><li>Прогрев этапа: " + measurementSettings.warmupOperationCount()
                + " операций; замеры: " + measurementSettings.measurementCount() + " серий по "
                + measurementSettings.operationsPerMeasurement() + " операций"
                + "</li><li>Порция потока: " + keyStreamBlockLengthBytes + " байт</li></ul>";
    }

    /**
     * Формирует статистику этапов; размер порции учитывается только для генерации потока.
     *
     * @param measurements этапы, восстановленные из исходного CSV
     * @return строки HTML-таблицы
     */
    private String buildMeasurementRows(List<StageMeasurement> measurements) {
        StringBuilder rows = new StringBuilder();
        for (StageMeasurement measurement : measurements) {
            rows.append("<tr><td>").append(escapeHtml(measurement.algorithmName()))
                    .append("</td><td>").append(escapeHtml(measurement.stageName()))
                    .append("</td><td>").append(formatNumber(measurement.calculateMedianCyclesPerOperation()))
                    .append("</td><td>").append(formatNumber(measurement.calculateMeanCyclesPerOperation()))
                    .append("</td><td>").append(formatNumber(measurement.calculateMinimumCyclesPerOperation()))
                    .append("</td><td>").append(formatNumber(measurement.calculateMaximumCyclesPerOperation()))
                    .append("</td><td>");
            if (measurement.stageName().equals(StreamCipherBenchmark.KEY_STREAM_STAGE)) {
                rows.append(String.format(Locale.ROOT, "%.4f",
                        measurement.calculateMedianCyclesPerOperation() / keyStreamBlockLengthBytes));
            } else {
                rows.append("—");
            }
            rows.append("</td></tr>\n");
        }
        return rows.toString();
    }

    /**
     * Сравнивает медианы двух общих этапов отдельно, без смешения разных единиц.
     *
     * @param measurements этапы, восстановленные из исходного CSV
     * @return вывод для одного локального запуска
     */
    private String buildConclusion(List<StageMeasurement> measurements) {
        StageMeasurement fastestInitialization = findFastestStage(measurements, StreamCipherBenchmark.INITIALIZATION_STAGE);
        StageMeasurement fastestGeneration = findFastestStage(measurements, StreamCipherBenchmark.KEY_STREAM_STAGE);
        return "<p>В этом запуске наименьшая медиана полной инициализации у <strong>"
                + escapeHtml(fastestInitialization.algorithmName()) + "</strong>: "
                + formatNumber(fastestInitialization.calculateMedianCyclesPerOperation()) + " цикл/операцию.</p>"
                + "<p>Наименьшая медиана генерации потока у <strong>"
                + escapeHtml(fastestGeneration.algorithmName()) + "</strong>: "
                + String.format(Locale.ROOT, "%.4f",
                fastestGeneration.calculateMedianCyclesPerOperation() / keyStreamBlockLengthBytes)
                + " цикл/байт. Выбор по одному этапу может отличаться от выбора по другому.</p>"
                + "<p>Это измерение выбранных Java-реализаций в одной JVM на данном компьютере. "
                + "На результат влияют JIT, сборка мусора, адаптеры и системный счётчик. "
                + "Один запуск не устанавливает универсальное преимущество алгоритма.</p>";
    }

    /**
     * Находит минимальную медиану среди результатов одного общего этапа.
     *
     * @param measurements завершённые этапы
     * @param stageName сравниваемый этап
     * @return результат с наименьшей медианой
     */
    private static StageMeasurement findFastestStage(List<StageMeasurement> measurements, String stageName) {
        return measurements.stream().filter(measurement -> measurement.stageName().equals(stageName))
                .min(Comparator.comparingDouble(StageMeasurement::calculateMedianCyclesPerOperation)).orElseThrow();
    }

    /**
     * Форматирует циклы с точкой как десятичным разделителем.
     *
     * @param value число циклов
     * @return значение с двумя знаками после точки
     */
    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Экранирует текст перед включением в HTML.
     *
     * @param value исходный текст
     * @return текст с экранированными специальными символами
     */
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
