package securetransfer.rgz;

import securetransfer.common.measurement.BenchmarkRunner;
import securetransfer.common.measurement.BenchmarkSettings;
import securetransfer.common.measurement.CpuCycleCounter;
import securetransfer.common.measurement.CpuCycleCounterFactory;
import securetransfer.common.measurement.StageMeasurement;
import securetransfer.rgz.cipher.StreamCipherAlgorithm;
import securetransfer.rgz.config.RgzSettings;
import securetransfer.rgz.measurement.CipherBenchmarkReportWriter;
import securetransfer.rgz.measurement.StreamCipherBenchmark;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Точка входа сравнения четырёх шифров РГЗ с сохранением CSV и HTML. */
public final class CipherBenchmarkMain {
    /** Запрещает создание экземпляров точки входа. */
    private CipherBenchmarkMain() {
    }

    /**
     * Запускает все этапы с настройками из RgzSettings, без аргументов консоли.
     *
     * @param commandLineArguments аргументы не используются; настройки задаются именованными константами
     */
    public static void main(String[] commandLineArguments) {
        BenchmarkSettings settings = RgzSettings.CIPHER_BENCHMARK_SETTINGS;
        int blockLengthBytes = RgzSettings.KEY_STREAM_BLOCK_LENGTH_BYTES;
        try (CpuCycleCounter cycleCounter = CpuCycleCounterFactory.createForCurrentPlatform()) {
            cycleCounter.readCurrentThreadCycles();
            System.out.println("Счётчик: " + cycleCounter.getDescription());
            System.out.println("Четыре шифра, ключи 128 бит. Порция потока: " + blockLengthBytes + " байт.");
            System.out.println("Полная инициализация включает создание объекта, подготовку ключа и IV.");
            System.out.println("Случайные входы, буферы, файлы и вывод подготавливаются вне замеров.");
            BenchmarkRunner measurementRunner = new BenchmarkRunner(cycleCounter, settings);
            StageMeasurement counterOverhead = measurementRunner.measureCounterOverhead();
            int preparedInputCount = settings.warmupOperationCount()
                    + settings.measurementCount() * settings.operationsPerMeasurement();
            StreamCipherBenchmark benchmark = new StreamCipherBenchmark(
                    measurementRunner, blockLengthBytes, preparedInputCount
            );
            List<StageMeasurement> measurements = new ArrayList<>();
            for (StreamCipherAlgorithm algorithm : StreamCipherAlgorithm.values()) {
                measurements.addAll(benchmark.measureAlgorithm(algorithm));
            }
            CipherBenchmarkReportWriter reportWriter = new CipherBenchmarkReportWriter(
                    settings, blockLengthBytes, cycleCounter.getDescription()
            );
            Path reportPath = reportWriter.saveReport(measurements, counterOverhead);
            System.out.println("\nВсе этапы завершены: " + measurements.size());
            System.out.println("Отчёт: " + reportPath);
            System.out.println("Исходные замеры: " + reportPath.resolveSibling("measurements.csv"));
        } catch (UnsupportedOperationException | IllegalStateException | LinkageError measurementFailure) {
            System.err.println("Измерения не завершены: " + measurementFailure.getMessage());
        } catch (IOException reportFailure) {
            System.err.println("Не удалось сохранить результаты: " + reportFailure.getMessage());
        }
    }
}
