package securetransfer;

import securetransfer.core.GroupParameters;
import securetransfer.measurement.BenchmarkRunner;
import securetransfer.measurement.BenchmarkReportWriter;
import securetransfer.measurement.BenchmarkSettings;
import securetransfer.measurement.CpuCycleCounter;
import securetransfer.measurement.CpuCycleCounterFactory;
import securetransfer.measurement.KeyAgreementBenchmark;
import securetransfer.measurement.StageMeasurement;
import securetransfer.storage.GroupParameterFileStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/** Запуск прогрева JVM и повторных замеров математических этапов DH и MQV. */
public class BenchmarkMain {
    /**
     * Подготавливает параметры, запускает девять этапов и сохраняет CSV и HTML-отчёт.
     */
    public static void main(String[] args) {
        BenchmarkSettings settings = BenchmarkSettings.createDefault();
        try (CpuCycleCounter cycleCounter = CpuCycleCounterFactory.createForCurrentPlatform()) {
            // Проверяем доступ к счётчику до потенциально долгой генерации параметров.
            cycleCounter.readCurrentThreadCycles();
            System.out.println("Счётчик подключён: " + cycleCounter.getDescription());
            System.out.printf("Размеры чисел: p = %d бит; q подгруппы = %d бит.%n",
                    settings.modulusBitLength(), settings.subgroupOrderBitLength());
            System.out.println("Подготовка параметров вне измерений. Первая генерация может занять время.");

            GroupParameters fullGroupParameters = loadFullGroupParameters(settings);
            GroupParameters subgroupParameters = loadSubgroupParameters(settings);
            System.out.printf("Фактические размеры: полная группа p/q = %d/%d; подгруппа p/q = %d/%d бит.%n",
                    fullGroupParameters.primeModulus().bitLength(), fullGroupParameters.subgroupOrder().bitLength(),
                    subgroupParameters.primeModulus().bitLength(), subgroupParameters.subgroupOrder().bitLength());
            System.out.println("Каждый замер относится к одному участнику; сеть, файлы и вывод в него не входят.");
            System.out.println("Служебные затраты показываются отдельно; они не вычитаются из показаний.");

            BenchmarkRunner measurementRunner = new BenchmarkRunner(cycleCounter, settings);
            StageMeasurement counterOverhead = measurementRunner.measureCounterOverhead();
            KeyAgreementBenchmark benchmark = new KeyAgreementBenchmark(measurementRunner);
            List<StageMeasurement> stageMeasurements = new ArrayList<>();
            stageMeasurements.addAll(benchmark.measureFullGroupDh(fullGroupParameters));
            stageMeasurements.addAll(benchmark.measureSubgroupDh(subgroupParameters));
            stageMeasurements.addAll(benchmark.measureMqv(subgroupParameters));

            System.out.printf("%nЗавершено: %d этапов; в каждом %d замеров по %d операций.%n",
                    stageMeasurements.size(), settings.measurementCount(), settings.operationsPerMeasurement());
            System.out.printf("Служебных замеров: %d.%n",
                    counterOverhead.totalCyclesPerMeasurement().length);
            BenchmarkReportWriter reportWriter = new BenchmarkReportWriter(
                    settings, cycleCounter.getDescription(), fullGroupParameters, subgroupParameters
            );
            Path htmlReportPath = reportWriter.saveReport(stageMeasurements, counterOverhead);
            System.out.println("HTML-отчёт: " + htmlReportPath);
            System.out.println("Исходные замеры CSV: " + htmlReportPath.resolveSibling("measurements.csv"));
        } catch (UnsupportedOperationException | IllegalStateException | LinkageError counterUnavailable) {
            System.out.println("Измерения остановлены: " + counterUnavailable.getMessage());
            System.out.println("Полная серия не завершена. Локальные алгоритмы и сеть доступны отдельно.");
        } catch (IOException | GeneralSecurityException preparationFailure) {
            System.err.println("Не удалось подготовить измерения или сохранить отчёт: " + preparationFailure.getMessage());
        }
    }

    /**
     * Загружает или генерирует отдельные параметры полной группы для выбранного размера.
     *
     * @param settings параметры измерения
     * @return проверенные параметры полной группы нужной битовой длины
     * @throws IOException если файл параметров недоступен
     */
    private static GroupParameters loadFullGroupParameters(BenchmarkSettings settings) throws IOException {
        Path parametersPath = Path.of("data", "params", "benchmark",
                "full-group-" + settings.modulusBitLength() + ".properties");
        GroupParameters parameters = GroupParameterFileStorage.loadOrGenerateFullGroupParameters(
                parametersPath, settings.modulusBitLength()
        );
        if (parameters.primeModulus().bitLength() != settings.modulusBitLength()) {
            throw new IllegalArgumentException("Размер p в файле не совпал с настройками: " + parametersPath);
        }
        return parameters;
    }

    /**
     * Загружает или генерирует общую для DH и MQV подгруппу, отдельно от учебных файлов.
     *
     * @param settings размеры p и q
     * @return проверенные параметры подгруппы нужных размеров
     * @throws IOException если файл параметров недоступен
     */
    private static GroupParameters loadSubgroupParameters(BenchmarkSettings settings) throws IOException {
        Path parametersPath = Path.of("data", "params", "benchmark",
                "subgroup-" + settings.modulusBitLength() + "-" + settings.subgroupOrderBitLength() + ".properties");
        GroupParameters parameters = GroupParameterFileStorage.loadOrGenerateSubgroupParameters(
                parametersPath, settings.modulusBitLength(), settings.subgroupOrderBitLength()
        );
        if (parameters.primeModulus().bitLength() != settings.modulusBitLength()
                || parameters.subgroupOrder().bitLength() != settings.subgroupOrderBitLength()) {
            throw new IllegalArgumentException("Размеры p/q в файле не совпали с настройками: " + parametersPath);
        }
        return parameters;
    }
}
