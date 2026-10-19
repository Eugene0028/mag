package securetransfer.rgz.measurement;

import securetransfer.common.core.GroupParameters;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Отчёт о стоимости SHA3-256 и получения ключа относительно операций MQV. */
public final class KeyDerivationReportWriter {
    /** Проверенные параметры группы, использованные для всех этапов запуска. */
    private final GroupParameters subgroupParameters;

    /** Имя и версия фактически выбранного провайдера SHA3-256. */
    private final String hashProviderDescription;

    /** Описание источника процессорных циклов. */
    private final String counterDescription;

    /**
     * Сохраняет параметры проведённого эксперимента.
     *
     * @param subgroupParameters параметры MQV
     * @param hashProviderDescription провайдер SHA3-256
     * @param counterDescription источник процессорных циклов
     */
    public KeyDerivationReportWriter(
            GroupParameters subgroupParameters, String hashProviderDescription, String counterDescription
    ) {
        this.subgroupParameters = subgroupParameters;
        this.hashProviderDescription = hashProviderDescription;
        this.counterDescription = counterDescription;
    }

    /**
     * Сохраняет исходные циклы и строит таблицу и отношения медиан по сохранённому CSV.
     *
     * @param measurements семь завершённых этапов
     * @param counterOverhead отдельная служебная серия
     * @return абсолютный путь к HTML-отчёту
     * @throws IOException если запись или загрузка шаблона не удалась
     */
    public Path saveReport(List<StageMeasurement> measurements, StageMeasurement counterOverhead) throws IOException {
        LocalDateTime reportTime = LocalDateTime.now();
        String directoryName = reportTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path reportDirectory = RgzSettings.DATA_PATHS.reportsDirectory().resolve("key-derivation")
                .resolve(directoryName).toAbsolutePath().normalize();
        Files.createDirectories(reportDirectory);
        Path csvPath = reportDirectory.resolve("measurements.csv");
        BenchmarkCsvFileStorage.saveMeasurements(csvPath, measurements, counterOverhead);
        BenchmarkCsvFileStorage.StoredMeasurements stored = BenchmarkCsvFileStorage.loadMeasurements(csvPath);
        String template;
        try (InputStream templateStream = KeyDerivationReportWriter.class
                .getResourceAsStream("/rgz/key-derivation-report.html")) {
            if (templateStream == null) {
                throw new IOException("Шаблон отчёта о получении ключа не найден в ресурсах");
            }
            template = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String html = template.replace("{{DATE}}", escapeHtml(reportTime.toString()))
                .replace("{{KEY_LENGTH}}", Integer.toString(RgzSettings.DERIVED_KEY_LENGTH_BYTES))
                .replace("{{ENVIRONMENT}}", buildEnvironmentDescription())
                .replace("{{ROWS}}", buildMeasurementRows(stored.stages()))
                .replace("{{CONCLUSION}}", buildComparison(stored.stages()))
                .replace("{{OVERHEAD}}", formatNumber(stored.overhead().calculateMedianCyclesPerOperation()))
                .replace("{{RAW_CSV}}", escapeHtml(Files.readString(csvPath, StandardCharsets.UTF_8)));
        Path htmlPath = reportDirectory.resolve("report.html");
        Files.writeString(htmlPath, html, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return htmlPath;
    }

    /** @return условия измерения, размеры данных и фактический провайдер в HTML */
    private String buildEnvironmentDescription() {
        BenchmarkSettings settings = RgzSettings.KEY_DERIVATION_BENCHMARK_SETTINGS;
        int encodedSecretLengthBytes = (subgroupParameters.primeModulus().bitLength() - 1) / Byte.SIZE + 1;
        return "<ul><li>Счётчик: " + escapeHtml(counterDescription)
                + "</li><li>ОС и JVM: " + escapeHtml(System.getProperty("os.name") + " "
                + System.getProperty("os.version") + "; " + System.getProperty("os.arch")
                + "; Java " + System.getProperty("java.version") + "; " + System.getProperty("java.vendor"))
                + "</li><li>SHA3-256: " + escapeHtml(hashProviderDescription)
                + "</li><li>Размеры p/q: " + subgroupParameters.primeModulus().bitLength() + "/"
                + subgroupParameters.subgroupOrder().bitLength() + " бит; вход хеша: " + encodedSecretLengthBytes
                + " байт; хеш: 32 байта; ключ: " + RgzSettings.DERIVED_KEY_LENGTH_BYTES + " байт"
                + "</li><li>Прогрев: " + settings.warmupOperationCount() + " операций; измерения: "
                + settings.measurementCount() + " серий по " + settings.operationsPerMeasurement() + " операций</li></ul>";
    }

    /**
     * Формирует статистику в одной единице: циклы на операцию.
     *
     * @param measurements этапы из CSV
     * @return строки HTML-таблицы
     */
    private static String buildMeasurementRows(List<StageMeasurement> measurements) {
        StringBuilder rows = new StringBuilder();
        for (StageMeasurement measurement : measurements) {
            rows.append("<tr><td>").append(escapeHtml(measurement.algorithmName()))
                    .append("</td><td>").append(escapeHtml(measurement.stageName()))
                    .append("</td><td>").append(formatNumber(measurement.calculateMedianCyclesPerOperation()))
                    .append("</td><td>").append(formatNumber(measurement.calculateMeanCyclesPerOperation()))
                    .append("</td><td>").append(formatNumber(measurement.calculateMinimumCyclesPerOperation()))
                    .append("</td><td>").append(formatNumber(measurement.calculateMaximumCyclesPerOperation()))
                    .append("</td></tr>\n");
        }
        return rows.toString();
    }

    /**
     * Сравнивает медиану хеша с тремя этапами MQV и отдельно показывает всю обработку секрета.
     *
     * @param measurements этапы одного запуска
     * @return вывод с отношениями медиан
     */
    private static String buildComparison(List<StageMeasurement> measurements) {
        double hashMedian = findStage(measurements, KeyDerivationBenchmark.HASH_STAGE)
                .calculateMedianCyclesPerOperation();
        double derivationMedian = findStage(measurements, KeyDerivationBenchmark.DERIVATION_STAGE)
                .calculateMedianCyclesPerOperation();
        StringBuilder comparison = new StringBuilder("<p>Медиана SHA3-256 готовых байтов: <strong>")
                .append(formatNumber(hashMedian)).append(" цикл/операцию</strong>.</p><ul>");
        for (StageMeasurement measurement : measurements) {
            if (measurement.algorithmName().equals("MQV")) {
                comparison.append("<li>Хеширование / «").append(escapeHtml(measurement.stageName()))
                        .append("»: ").append(formatNumber(100.0 * hashMedian
                        / measurement.calculateMedianCyclesPerOperation())).append("%.</li>");
            }
        }
        return comparison.append("</ul><p>Получение ключа из готового секрета, включая кодирование, хеш, ")
                .append("копирование ключа и очистку временных массивов: ")
                .append(formatNumber(derivationMedian)).append(" цикл/операцию по медиане.</p>")
                .append("<p>Отношения относятся к медианам одного локального запуска. Медианы отдельных ")
                .append("этапов не складываются в стоимость всей цепочки; полная цепочка измерена отдельно.</p>")
                .toString();
    }

    /**
     * Находит результат этапа по его уникальному имени.
     *
     * @param measurements завершённые этапы
     * @param stageName искомый этап
     * @return найденное измерение
     */
    private static StageMeasurement findStage(List<StageMeasurement> measurements, String stageName) {
        return measurements.stream().filter(measurement -> measurement.stageName().equals(stageName))
                .findFirst().orElseThrow();
    }

    /**
     * Форматирует числовое значение с двумя знаками после точки.
     *
     * @param value число циклов или процент
     * @return строка с фиксированным десятичным разделителем
     */
    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Экранирует текстовые данные для HTML.
     *
     * @param value исходный текст
     * @return текст с экранированными специальными символами
     */
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
