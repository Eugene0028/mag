package securetransfer.rgz;

import securetransfer.common.config.GroupGenerationSettings;
import securetransfer.common.core.GroupParameters;
import securetransfer.common.measurement.BenchmarkRunner;
import securetransfer.common.measurement.BenchmarkSettings;
import securetransfer.common.measurement.CpuCycleCounter;
import securetransfer.common.measurement.CpuCycleCounterFactory;
import securetransfer.common.measurement.StageMeasurement;
import securetransfer.common.storage.GroupParameterFileStorage;
import securetransfer.rgz.config.RgzSettings;
import securetransfer.rgz.key.MqvKeyDerivation;
import securetransfer.rgz.key.Sha3Hasher;
import securetransfer.rgz.measurement.KeyDerivationBenchmark;
import securetransfer.rgz.measurement.KeyDerivationReportWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

/** Точка входа сравнения хеширования с операциями выработки ключа MQV. */
public final class KeyDerivationBenchmarkMain {
    /** Запрещает создание экземпляров точки входа. */
    private KeyDerivationBenchmarkMain() {
    }

    /**
     * Готовит параметры РГЗ, выполняет замеры и сохраняет CSV и HTML.
     *
     * @param commandLineArguments не используются; настройки заданы в RgzSettings
     */
    public static void main(String[] commandLineArguments) {
        BenchmarkSettings settings = RgzSettings.KEY_DERIVATION_BENCHMARK_SETTINGS;
        GroupGenerationSettings groupSettings = RgzSettings.KEY_AGREEMENT_GROUP_SETTINGS;
        try (CpuCycleCounter cycleCounter = CpuCycleCounterFactory.createForCurrentPlatform()) {
            cycleCounter.readCurrentThreadCycles();
            Sha3Hasher hashFunction = new Sha3Hasher();
            System.out.println("Счётчик: " + cycleCounter.getDescription());
            System.out.println("SHA3-256, провайдер: " + hashFunction.getProviderDescription());
            System.out.println("Подготовка параметров вне замеров. Первая генерация может занять время.");
            GroupParameters subgroupParameters = GroupParameterFileStorage.loadOrGenerateSubgroupParameters(
                    RgzSettings.DATA_PATHS.subgroupParametersFile(),
                    groupSettings.modulusBitLength(), groupSettings.subgroupOrderBitLength()
            );
            int encodedSecretLengthBytes = new MqvKeyDerivation(subgroupParameters, hashFunction)
                    .getEncodedSecretLengthBytes();
            System.out.printf("p/q = %d/%d бит; секрет = %d байт; ключ шифра = %d байт.%n",
                    subgroupParameters.primeModulus().bitLength(), subgroupParameters.subgroupOrder().bitLength(),
                    encodedSecretLengthBytes, RgzSettings.DERIVED_KEY_LENGTH_BYTES);
            BenchmarkRunner measurementRunner = new BenchmarkRunner(cycleCounter, settings);
            StageMeasurement counterOverhead = measurementRunner.measureCounterOverhead();
            KeyDerivationBenchmark benchmark = new KeyDerivationBenchmark(measurementRunner, hashFunction);
            List<StageMeasurement> measurements = benchmark.measureKeyDerivation(
                    subgroupParameters, RgzSettings.DERIVED_KEY_LENGTH_BYTES
            );
            KeyDerivationReportWriter reportWriter = new KeyDerivationReportWriter(
                    subgroupParameters, hashFunction.getProviderDescription(), cycleCounter.getDescription()
            );
            Path reportPath = reportWriter.saveReport(measurements, counterOverhead);
            System.out.println("\nВсе этапы завершены: " + measurements.size());
            System.out.println("Отчёт: " + reportPath);
            System.out.println("Исходные замеры: " + reportPath.resolveSibling("measurements.csv"));
        } catch (UnsupportedOperationException | IllegalStateException | LinkageError measurementFailure) {
            System.err.println("Измерения не завершены: " + measurementFailure.getMessage());
        } catch (IOException | GeneralSecurityException preparationFailure) {
            System.err.println("Не удалось подготовить измерения или сохранить отчёт: " + preparationFailure.getMessage());
        }
    }
}
