package securetransfer.lab1;

import securetransfer.common.config.GroupGenerationSettings;
import securetransfer.common.core.GroupParameters;
import securetransfer.common.measurement.BenchmarkRunner;
import securetransfer.common.measurement.BenchmarkSettings;
import securetransfer.common.measurement.CpuCycleCounter;
import securetransfer.common.measurement.CpuCycleCounterFactory;
import securetransfer.common.measurement.KeyAgreementBenchmark;
import securetransfer.common.measurement.StageMeasurement;
import securetransfer.common.storage.GroupParameterFileStorage;
import securetransfer.lab1.config.Lab1Settings;
import securetransfer.lab1.measurement.BenchmarkReportWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/** Запуск прогрева JVM и повторных замеров математических этапов DH и MQV. */
public class BenchmarkMain {
    /**
     * Подготавливает параметры, запускает девять этапов и сохраняет CSV и HTML-отчёт.
     *
     * @param commandLineArguments аргументы запуска; настройки задаются в Lab1Settings
     */
    public static void main(String[] commandLineArguments) {
        BenchmarkSettings settings = Lab1Settings.BENCHMARK_SETTINGS;
        GroupGenerationSettings groupSettings = Lab1Settings.BENCHMARK_GROUP_SETTINGS;
        try (CpuCycleCounter cycleCounter = CpuCycleCounterFactory.createForCurrentPlatform()) {
            // Проверяем доступ к счётчику до потенциально долгой генерации параметров.
            cycleCounter.readCurrentThreadCycles();
            System.out.println("Счётчик подключён: " + cycleCounter.getDescription());
            System.out.printf("Размеры чисел: p = %d бит; q подгруппы = %d бит.%n",
                    groupSettings.modulusBitLength(), groupSettings.subgroupOrderBitLength());
            System.out.println("Подготовка параметров вне измерений. Первая генерация может занять время.");

            GroupParameters fullGroupParameters = loadFullGroupParameters(groupSettings);
            GroupParameters subgroupParameters = loadSubgroupParameters(groupSettings);
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
    private static GroupParameters loadFullGroupParameters(GroupGenerationSettings settings) throws IOException {
        Path parametersPath = Lab1Settings.DATA_PATHS.benchmarkParametersDirectory().resolve(
                "full-group-" + settings.modulusBitLength() + ".properties");
        return GroupParameterFileStorage.loadOrGenerateFullGroupParameters(
                parametersPath, settings.modulusBitLength()
        );
    }

    /**
     * Загружает или генерирует общую для DH и MQV подгруппу, отдельно от учебных файлов.
     *
     * @param settings размеры p и q
     * @return проверенные параметры подгруппы нужных размеров
     * @throws IOException если файл параметров недоступен
     */
    private static GroupParameters loadSubgroupParameters(GroupGenerationSettings settings) throws IOException {
        Path parametersPath = Lab1Settings.DATA_PATHS.benchmarkParametersDirectory().resolve(
                "subgroup-" + settings.modulusBitLength() + "-" + settings.subgroupOrderBitLength() + ".properties");
        return GroupParameterFileStorage.loadOrGenerateSubgroupParameters(
                parametersPath, settings.modulusBitLength(), settings.subgroupOrderBitLength()
        );
    }
}
