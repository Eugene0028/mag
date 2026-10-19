package securetransfer.storage;

import securetransfer.measurement.StageMeasurement;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сохранение исходных циклов серий в UTF-8 CSV и восстановление результатов для HTML-отчёта.
 * В файле сохраняются только названия этапов, размеры серий и циклы, без ключей участников.
 */
public final class BenchmarkCsvFileStorage {
    /** Поля одной строки: тип серии, алгоритм, этап, номер замера, размер серии, циклы серии. */
    private static final List<String> COLUMN_NAMES = List.of(
            "kind", "algorithm", "stage", "measurement_index", "operations", "cycles"
    );

    /** Запрещает создание экземпляров класса статических операций. */
    private BenchmarkCsvFileStorage() {
    }

    /**
     * Создаёт CSV с исходными целочисленными показаниями и отдельными служебными замерами.
     *
     * @param csvFilePath путь к новому файлу
     * @param stageMeasurements завершённые этапы алгоритмов
     * @param counterOverhead показания пустой операции
     * @throws IOException если создать или записать файл не удалось
     */
    public static void saveMeasurements(
            Path csvFilePath,
            List<StageMeasurement> stageMeasurements,
            StageMeasurement counterOverhead
    ) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                csvFilePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
        )) {
            writer.write(String.join(",", COLUMN_NAMES));
            writer.write("\r\n");
            writeMeasurementRows(writer, "overhead", counterOverhead);
            for (StageMeasurement measurement : stageMeasurements) {
                writeMeasurementRows(writer, "stage", measurement);
            }
        }
    }

    /**
     * Восстанавливает этапы из CSV; именно эти показания используются для HTML-таблицы и выводов.
     *
     * @param csvFilePath ранее сохранённый CSV
     * @return измеренные этапы и отдельная служебная серия
     * @throws IOException если файл повреждён, порядок замеров нарушен или служебная серия отсутствует
     */
    public static StoredMeasurements loadMeasurements(Path csvFilePath) throws IOException {
        List<List<String>> rows = parseCsv(Files.readString(csvFilePath, StandardCharsets.UTF_8));
        if (rows.isEmpty() || !rows.get(0).equals(COLUMN_NAMES)) {
            throw new IOException("Заголовок CSV с замерами не соответствует ожидаемому формату.");
        }
        Map<MeasurementIdentity, List<Long>> cyclesByMeasurement = new LinkedHashMap<>();
        try {
            for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                if (row.size() != COLUMN_NAMES.size()) {
                    throw new IOException("Неверное число столбцов CSV в записи " + (rowIndex + 1));
                }
                String kind = row.get(0);
                if (!kind.equals("stage") && !kind.equals("overhead")) {
                    throw new IOException("Неизвестный тип замера: " + kind);
                }
                int measurementIndex = Integer.parseInt(row.get(3));
                int operationCount = Integer.parseInt(row.get(4));
                long measuredCycles = Long.parseLong(row.get(5));
                if (operationCount < 1 || measuredCycles <= 0) {
                    throw new IOException("Размер серии и циклы должны быть положительными.");
                }
                MeasurementIdentity identity = new MeasurementIdentity(
                        kind, row.get(1), row.get(2), operationCount
                );
                List<Long> cycleCounts = cyclesByMeasurement.computeIfAbsent(identity, ignored -> new ArrayList<>());
                if (measurementIndex != cycleCounts.size() + 1) {
                    throw new IOException("Нарушена последовательность замеров этапа: " + identity.stageName());
                }
                cycleCounts.add(measuredCycles);
            }
        } catch (NumberFormatException invalidNumber) {
            throw new IOException("В CSV обнаружено неверное числовое значение.", invalidNumber);
        }

        List<StageMeasurement> stages = new ArrayList<>();
        StageMeasurement overhead = null;
        for (Map.Entry<MeasurementIdentity, List<Long>> entry : cyclesByMeasurement.entrySet()) {
            MeasurementIdentity identity = entry.getKey();
            long[] cycleCounts = entry.getValue().stream().mapToLong(Long::longValue).toArray();
            StageMeasurement measurement = new StageMeasurement(
                    identity.algorithmName(), identity.stageName(), identity.operationsPerMeasurement(), cycleCounts
            );
            if (identity.kind().equals("overhead")) {
                if (overhead != null) {
                    throw new IOException("CSV содержит больше одной служебной серии.");
                }
                overhead = measurement;
            } else {
                stages.add(measurement);
            }
        }
        if (overhead == null || stages.isEmpty()) {
            throw new IOException("В CSV отсутствуют служебные замеры или этапы алгоритмов.");
        }
        return new StoredMeasurements(stages, overhead);
    }

    /**
     * Записывает каждое исходное показание отдельной строкой.
     *
     * @param writer открытый CSV-файл
     * @param kind тип измерения: stage или overhead
     * @param measurement исходные показания одного этапа
     * @throws IOException если запись не удалась
     */
    private static void writeMeasurementRows(
            BufferedWriter writer,
            String kind,
            StageMeasurement measurement
    ) throws IOException {
        long[] cycleCounts = measurement.totalCyclesPerMeasurement();
        for (int measurementIndex = 0; measurementIndex < cycleCounts.length; measurementIndex++) {
            writer.write(String.join(",",
                    kind,
                    quoteCsvField(measurement.algorithmName()),
                    quoteCsvField(measurement.stageName()),
                    Integer.toString(measurementIndex + 1),
                    Integer.toString(measurement.operationsPerMeasurement()),
                    Long.toString(cycleCounts[measurementIndex])
            ));
            writer.write("\r\n");
        }
    }

    /**
     * Экранирует текстовое поле CSV двойными кавычками.
     *
     * @param value название алгоритма или этапа
     * @return поле с удвоенными внутренними кавычками
     */
    private static String quoteCsvField(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * Разбирает CSV с запятыми, двойными кавычками, переносами строк и удвоенными кавычками.
     *
     * @param csvContent содержимое файла
     * @return строки и их поля
     * @throws IOException если кавычки не закрыты или текст после закрывающей кавычки некорректен
     */
    private static List<List<String>> parseCsv(String csvContent) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean insideQuotes = false;
        boolean quotedFieldEnded = false;
        for (int characterIndex = 0; characterIndex < csvContent.length(); characterIndex++) {
            char character = csvContent.charAt(characterIndex);
            if (insideQuotes) {
                if (character == '"') {
                    if (characterIndex + 1 < csvContent.length()
                            && csvContent.charAt(characterIndex + 1) == '"') {
                        field.append('"');
                        characterIndex++;
                    } else {
                        insideQuotes = false;
                        quotedFieldEnded = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == ',' || character == '\r' || character == '\n') {
                row.add(field.toString());
                field.setLength(0);
                quotedFieldEnded = false;
                if (character != ',') {
                    rows.add(List.copyOf(row));
                    row.clear();
                    if (character == '\r' && characterIndex + 1 < csvContent.length()
                            && csvContent.charAt(characterIndex + 1) == '\n') {
                        characterIndex++;
                    }
                }
            } else if (character == '"' && field.length() == 0 && !quotedFieldEnded) {
                insideQuotes = true;
            } else {
                if (quotedFieldEnded || character == '"') {
                    throw new IOException("Некорректные кавычки в CSV.");
                }
                field.append(character);
            }
        }
        if (insideQuotes) {
            throw new IOException("В CSV не закрыто текстовое поле.");
        }
        if (field.length() > 0 || quotedFieldEnded || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(List.copyOf(row));
        }
        return rows;
    }

    /**
     * Ключ объединения строк CSV в один измеренный этап.
     *
     * @param kind тип серии
     * @param algorithmName название алгоритма
     * @param stageName название этапа
     * @param operationsPerMeasurement число операций в серии
     */
    private record MeasurementIdentity(
            String kind,
            String algorithmName,
            String stageName,
            int operationsPerMeasurement
    ) {
    }

    /**
     * Восстановленные из CSV этапы и служебные измерения.
     *
     * @param stages измеренные этапы алгоритмов
     * @param overhead отдельные показания пустой операции
     */
    public record StoredMeasurements(List<StageMeasurement> stages, StageMeasurement overhead) {
        /** Сохраняет неизменяемый список этапов. */
        public StoredMeasurements {
            stages = List.copyOf(stages);
        }
    }
}
