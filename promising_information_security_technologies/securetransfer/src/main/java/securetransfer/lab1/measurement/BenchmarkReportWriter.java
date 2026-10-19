package securetransfer.lab1.measurement;

import securetransfer.common.core.GroupParameters;
import securetransfer.common.measurement.BenchmarkSettings;
import securetransfer.common.measurement.StageMeasurement;
import securetransfer.common.storage.BenchmarkCsvFileStorage;
import securetransfer.lab1.config.Lab1Settings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Создание CSV и автономного HTML-отчёта по завершённым измерениям одного запуска. */
public final class BenchmarkReportWriter {
    /** Шаблон, который IDEA копирует из src/main/resources в каталог классов. */
    private static final String HTML_TEMPLATE_RESOURCE = "/lab1/benchmark-report.html";

    /** Имена мест вставки данных внутри HTML-шаблона. */
    private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)}}");

    /** Русское форматирование отображаемых чисел. */
    private static final Locale REPORT_LOCALE = Locale.forLanguageTag("ru-RU");

    /** Настройки завершённого измерения. */
    private final BenchmarkSettings settings;

    /** Источник циклов и область учёта, предоставленные системной реализацией. */
    private final String counterDescription;

    /** Фактически использованные параметры полной группы. */
    private final GroupParameters fullGroupParameters;

    /** Общие для DH подгруппы и MQV параметры. */
    private final GroupParameters subgroupParameters;

    /**
     * Сохраняет условия запуска, необходимые для интерпретации измерений.
     *
     * @param settings количество повторений и размер измеряемой серии
     * @param counterDescription описание системного источника циклов
     * @param fullGroupParameters параметры полной группы DH
     * @param subgroupParameters параметры подгруппы DH и MQV
     */
    public BenchmarkReportWriter(
            BenchmarkSettings settings,
            String counterDescription,
            GroupParameters fullGroupParameters,
            GroupParameters subgroupParameters
    ) {
        this.settings = Objects.requireNonNull(settings);
        this.counterDescription = Objects.requireNonNull(counterDescription);
        this.fullGroupParameters = Objects.requireNonNull(fullGroupParameters);
        this.subgroupParameters = Objects.requireNonNull(subgroupParameters);
    }

    /**
     * Создаёт папку запуска, сохраняет CSV, перечитывает его и строит HTML из этих показаний.
     *
     * @param stageMeasurements успешно измеренные этапы алгоритмов
     * @param counterOverhead отдельные замеры пустой операции
     * @return абсолютный путь к готовому report.html
     * @throws IOException если сохранение CSV, загрузка шаблона или создание отчёта не удались
     */
    public Path saveReport(
            List<StageMeasurement> stageMeasurements,
            StageMeasurement counterOverhead
    ) throws IOException {
        String htmlTemplate = loadHtmlTemplate();
        ZonedDateTime reportCreatedAt = ZonedDateTime.now();
        String runDirectoryName = reportCreatedAt.format(DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path reportsDirectory = Files.createDirectories(Lab1Settings.DATA_PATHS.reportsDirectory());
        Path runDirectory = Files.createDirectory(reportsDirectory.resolve(runDirectoryName));
        Path csvFilePath = runDirectory.resolve("measurements.csv");
        BenchmarkCsvFileStorage.saveMeasurements(csvFilePath, stageMeasurements, counterOverhead);

        BenchmarkCsvFileStorage.StoredMeasurements stored = BenchmarkCsvFileStorage.loadMeasurements(csvFilePath);
        List<StageMeasurement> stages = stored.stages();
        StageMeasurement overhead = stored.overhead();
        int csvRowCount = overhead.totalCyclesPerMeasurement().length;
        for (StageMeasurement measurement : stages) {
            csvRowCount += measurement.totalCyclesPerMeasurement().length;
        }

        Map<String, String> reportValues = new LinkedHashMap<>();
        reportValues.put("REPORT_DATE", escapeHtml(
                reportCreatedAt.format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss XXX '['VV']'"))
        ));
        reportValues.put("ALGORITHM_COUNT",
                Long.toString(stages.stream().map(StageMeasurement::algorithmName).distinct().count()));
        reportValues.put("STAGE_COUNT", Integer.toString(stages.size()));
        reportValues.put("MEASUREMENT_COUNT", Integer.toString(settings.measurementCount()));
        reportValues.put("BATCH_SIZE", Integer.toString(settings.operationsPerMeasurement()));
        reportValues.put("MODULUS_BITS", Integer.toString(fullGroupParameters.primeModulus().bitLength()));
        reportValues.put("SUBGROUP_BITS", Integer.toString(subgroupParameters.subgroupOrder().bitLength()));
        reportValues.put("RESULT_ROWS", buildResultRows(stages));
        reportValues.put("CONCLUSIONS", buildConclusions(stages, overhead));
        reportValues.put("OVERHEAD_DESCRIPTION",
                "Пустая операция: медиана " + formatCycles(overhead.calculateMedianCyclesPerOperation())
                        + ", минимум " + formatCycles(overhead.calculateMinimumCyclesPerOperation())
                        + " циклов/операцию. Служебные затраты включены в результаты и не вычитаются. "
                        + "Пустая серия служит ориентиром, а не точной поправкой.");
        reportValues.put("RUN_METADATA", buildRunMetadata());
        reportValues.put("CSV_ROW_COUNT", Integer.toString(csvRowCount));
        reportValues.put("RAW_CSV", escapeHtml(Files.readString(csvFilePath, StandardCharsets.UTF_8)));

        Path htmlFilePath = runDirectory.resolve("report.html");
        Files.writeString(
                htmlFilePath, renderTemplate(htmlTemplate, reportValues), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
        );
        return htmlFilePath.toAbsolutePath();
    }

    /**
     * Строит строки таблицы из восстановленных CSV-показаний.
     *
     * @param measurements этапы текущего запуска
     * @return HTML строк таблицы с подсветкой минимальной медианы внутри каждого этапа
     */
    private static String buildResultRows(List<StageMeasurement> measurements) {
        Map<String, Double> minimumMedians = new LinkedHashMap<>();
        for (StageMeasurement measurement : measurements) {
            minimumMedians.merge(
                    measurement.stageName(), measurement.calculateMedianCyclesPerOperation(), Math::min
            );
        }
        StringBuilder rows = new StringBuilder();
        for (StageMeasurement measurement : measurements) {
            double median = measurement.calculateMedianCyclesPerOperation();
            boolean minimumForStage = Double.compare(median, minimumMedians.get(measurement.stageName())) == 0;
            rows.append("<tr><td>").append(escapeHtml(measurement.algorithmName()))
                    .append("</td><td>").append(escapeHtml(measurement.stageName()))
                    .append("</td><td class=\"numeric median")
                    .append(minimumForStage ? " best" : "").append("\">").append(formatCycles(median))
                    .append("</td><td class=\"numeric\">")
                    .append(formatCycles(measurement.calculateMeanCyclesPerOperation()))
                    .append("</td><td class=\"numeric\">")
                    .append(formatCycles(measurement.calculateMinimumCyclesPerOperation()))
                    .append("</td><td class=\"numeric\">")
                    .append(formatCycles(measurement.calculateMaximumCyclesPerOperation()))
                    .append("</td></tr>\n");
        }
        return rows.toString();
    }

    /**
     * Сопоставляет медианы одинаковых этапов и описывает наблюдения только этого запуска.
     *
     * @param measurements этапы, прочитанные из CSV
     * @param overhead отдельно измеренные служебные затраты
     * @return HTML пунктов выводов
     */
    private static String buildConclusions(List<StageMeasurement> measurements, StageMeasurement overhead) {
        Map<String, List<StageMeasurement>> measurementsByStage = new LinkedHashMap<>();
        for (StageMeasurement measurement : measurements) {
            measurementsByStage.computeIfAbsent(measurement.stageName(), ignored -> new ArrayList<>())
                    .add(measurement);
        }
        StringBuilder conclusions = new StringBuilder();
        for (Map.Entry<String, List<StageMeasurement>> entry : measurementsByStage.entrySet()) {
            List<StageMeasurement> sortedMeasurements = new ArrayList<>(entry.getValue());
            sortedMeasurements.sort(Comparator.comparingDouble(StageMeasurement::calculateMedianCyclesPerOperation));
            double smallestMedian = sortedMeasurements.get(0).calculateMedianCyclesPerOperation();
            double largestMedian = sortedMeasurements.get(sortedMeasurements.size() - 1)
                    .calculateMedianCyclesPerOperation();
            conclusions.append("<li><strong>").append(escapeHtml(entry.getKey())).append(".</strong> ");
            if (Double.compare(smallestMedian, largestMedian) == 0) {
                conclusions.append("Медианы всех алгоритмов совпали: ")
                        .append(formatCycles(smallestMedian)).append(" циклов/операцию.");
            } else {
                String smallestAlgorithms = algorithmsWithMedian(sortedMeasurements, smallestMedian);
                String largestAlgorithms = algorithmsWithMedian(sortedMeasurements, largestMedian);
                conclusions.append("Наименьшая медиана — ").append(escapeHtml(smallestAlgorithms))
                        .append(": ").append(formatCycles(smallestMedian))
                        .append(" циклов/операцию; наибольшая — ").append(escapeHtml(largestAlgorithms))
                        .append(": ").append(formatCycles(largestMedian))
                        .append(". Отношение наибольшей медианы к наименьшей — ")
                        .append(formatRatio(largestMedian / smallestMedian)).append(".");
            }
            conclusions.append("</li>\n");
        }

        StageMeasurement shortestStage = measurements.stream().min(Comparator.comparingDouble(
                StageMeasurement::calculateMedianCyclesPerOperation
        )).orElseThrow();
        double overheadToShortestRatio = overhead.calculateMedianCyclesPerOperation()
                / shortestStage.calculateMedianCyclesPerOperation();
        conclusions.append("<li><strong>Масштаб служебных затрат.</strong> Медиана пустой операции — ")
                .append(formatCycles(overhead.calculateMedianCyclesPerOperation()))
                .append(" циклов/операцию. Это ").append(formatCycles(overheadToShortestRatio * 100))
                .append("% от медианы самого короткого этапа: ")
                .append(escapeHtml(shortestStage.algorithmName())).append(", «")
                .append(escapeHtml(shortestStage.stageName())).append("». ")
                .append("Это ориентир для масштаба измерительного кода, а не точная доля его вклада. ")
                .append("Особенно осторожно следует интерпретировать небольшие различия коротких этапов.</li>\n");

        StageMeasurement widestRange = measurements.stream().max(Comparator.comparingDouble(
                measurement -> measurement.calculateMaximumCyclesPerOperation()
                        / measurement.calculateMinimumCyclesPerOperation()
        )).orElseThrow();
        conclusions.append("<li><strong>Диапазон показаний.</strong> Наибольшее отношение максимума к минимуму — у ")
                .append(escapeHtml(widestRange.algorithmName())).append(", этап «")
                .append(escapeHtml(widestRange.stageName())).append("»: ")
                .append(formatRatio(widestRange.calculateMaximumCyclesPerOperation()
                        / widestRange.calculateMinimumCyclesPerOperation()))
                .append(". Это характеристика разброса серий; причина отклонений отдельно не устанавливалась.</li>\n")
                .append("<li><strong>Границы сопоставления.</strong> В полной группе секрет выбирается по размеру p, ")
                .append("в подгруппе и MQV — по размеру q. Генерация временного ключа MQV использует ту же ")
                .append("формулу, что DH в подгруппе. Вычисление секрета MQV включает два модульных возведения ")
                .append("в степень и дополнительные операции, DH — одно. Стоимость полного сетевого обмена, ")
                .append("подготовки долговременных ключей и проверки подписи этими числами не определяется.</li>");
        return conclusions.toString();
    }

    /**
     * Собирает названия алгоритмов, имеющих заданную медиану, включая возможные совпадения.
     *
     * @param measurements сопоставляемые реализации одного этапа
     * @param median искомая медиана
     * @return названия через запятую
     */
    private static String algorithmsWithMedian(List<StageMeasurement> measurements, double median) {
        return measurements.stream()
                .filter(measurement -> Double.compare(measurement.calculateMedianCyclesPerOperation(), median) == 0)
                .map(StageMeasurement::algorithmName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Описывает размеры чисел, настройки, ОС и JVM без имён пользователей и секретных данных.
     *
     * @return HTML элементов списка условий запуска
     */
    private String buildRunMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Полная группа: p / q", fullGroupParameters.primeModulus().bitLength()
                + " / " + fullGroupParameters.subgroupOrder().bitLength() + " бит");
        metadata.put("Подгруппа DH и MQV: p / q", subgroupParameters.primeModulus().bitLength()
                + " / " + subgroupParameters.subgroupOrder().bitLength() + " бит");
        metadata.put("Прогрев этапа", settings.warmupOperationCount() + " операций");
        metadata.put("Измерение этапа", settings.measurementCount() + " серий по "
                + settings.operationsPerMeasurement() + " операций");
        metadata.put("Системный счётчик", counterDescription);
        metadata.put("Операционная система", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        metadata.put("Архитектура JVM", System.getProperty("os.arch"));
        metadata.put("Версия Java", System.getProperty("java.runtime.version", System.getProperty("java.version")));
        metadata.put("Поставщик JVM", System.getProperty("java.vendor"));
        metadata.put("Процессоров, доступных JVM", Integer.toString(Runtime.getRuntime().availableProcessors()));
        StringBuilder rows = new StringBuilder();
        metadata.forEach((name, value) -> rows.append("<div><dt>").append(escapeHtml(name))
                .append("</dt><dd>").append(escapeHtml(value)).append("</dd></div>\n"));
        return rows.toString();
    }

    /**
     * Читает включённый в приложение HTML-шаблон.
     *
     * @return текст шаблона в UTF-8
     * @throws IOException если ресурс не найден или не читается
     */
    private static String loadHtmlTemplate() throws IOException {
        try (InputStream templateInput = BenchmarkReportWriter.class.getResourceAsStream(HTML_TEMPLATE_RESOURCE)) {
            if (templateInput == null) {
                throw new IOException("HTML-шаблон не найден. Папка src/main/resources должна быть ресурсами модуля.");
            }
            return new String(templateInput.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * За один проход заменяет места вставки, не интерпретируя содержимое подставленных данных.
     *
     * @param template исходный HTML-шаблон
     * @param values подготовленные и экранированные значения
     * @return окончательный HTML
     * @throws IOException если в шаблоне обнаружено неизвестное имя поля
     */
    private static String renderTemplate(String template, Map<String, String> values) throws IOException {
        Matcher placeholderMatcher = TEMPLATE_PLACEHOLDER.matcher(template);
        StringBuilder renderedHtml = new StringBuilder();
        while (placeholderMatcher.find()) {
            String fieldName = placeholderMatcher.group(1);
            String replacement = values.get(fieldName);
            if (replacement == null) {
                throw new IOException("Неизвестное поле HTML-шаблона: " + fieldName);
            }
            placeholderMatcher.appendReplacement(renderedHtml, Matcher.quoteReplacement(replacement));
        }
        placeholderMatcher.appendTail(renderedHtml);
        return renderedHtml.toString();
    }

    /**
     * Форматирует число циклов на операцию с одним знаком после запятой.
     *
     * @param value вычисленная статистика
     * @return число для отображения в отчёте
     */
    private static String formatCycles(double value) {
        NumberFormat formatter = NumberFormat.getNumberInstance(REPORT_LOCALE);
        formatter.setMinimumFractionDigits(1);
        formatter.setMaximumFractionDigits(1);
        return formatter.format(value);
    }

    /**
     * Форматирует отношение двух положительных показаний.
     *
     * @param ratio отношение сравниваемых величин
     * @return число с двумя знаками после запятой
     */
    private static String formatRatio(double ratio) {
        return String.format(REPORT_LOCALE, "%.2f", ratio);
    }

    /**
     * Экранирует текст для безопасного включения в HTML.
     *
     * @param value произвольное название, описание ОС или содержимое CSV
     * @return текст с экранированными служебными символами
     */
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
