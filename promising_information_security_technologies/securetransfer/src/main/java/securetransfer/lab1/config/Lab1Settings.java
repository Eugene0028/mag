package securetransfer.lab1.config;

import securetransfer.common.config.ApplicationDataPaths;
import securetransfer.common.config.GroupGenerationSettings;
import securetransfer.common.measurement.BenchmarkSettings;

import java.nio.file.Path;

/** Настройки демонстраций и вычислительного эксперимента первой лабораторной работы. */
public final class Lab1Settings {
    /** Рабочие файлы первой лабораторной работы. */
    public static final ApplicationDataPaths DATA_PATHS = new ApplicationDataPaths(Path.of("data", "lab1"));

    /** Малые числа для разбора локального и сетевого обмена: p — 16 бит, q — 8 бит. */
    public static final GroupGenerationSettings DEMONSTRATION_GROUP_SETTINGS = new GroupGenerationSettings(16, 8);

    /** Размеры групп для сравнения DH и MQV: p — 1024 бита, q — 160 бит. */
    public static final GroupGenerationSettings BENCHMARK_GROUP_SETTINGS = new GroupGenerationSettings(1024, 160);

    /** Число операций прогрева каждого этапа лабораторной работы. */
    private static final int BENCHMARK_WARMUP_OPERATION_COUNT = 1000;

    /** Число сохраняемых серий каждого этапа. */
    private static final int BENCHMARK_MEASUREMENT_COUNT = 100;

    /** Число операций внутри одной измеряемой серии. */
    private static final int BENCHMARK_OPERATIONS_PER_MEASUREMENT = 10;

    /** Настройки повторных измерений, передаваемые общему исполнителю. */
    public static final BenchmarkSettings BENCHMARK_SETTINGS = new BenchmarkSettings(
            BENCHMARK_WARMUP_OPERATION_COUNT,
            BENCHMARK_MEASUREMENT_COUNT,
            BENCHMARK_OPERATIONS_PER_MEASUREMENT
    );

    /** Адрес сервера при запуске обоих участников на одном компьютере. */
    public static final String DEFAULT_SERVER_HOST = "127.0.0.1";

    /** Порт учебного сервера при запуске без аргументов. */
    public static final int DEFAULT_SERVER_PORT = 5055;

    /** Максимальное ожидание подключения клиента к серверу. */
    public static final int CONNECT_TIMEOUT_MILLISECONDS = 5_000;

    /** Максимальное ожидание очередного чтения из соединения. */
    public static final int READ_TIMEOUT_MILLISECONDS = 30_000;

    /** Запрещает создание экземпляров класса настроек. */
    private Lab1Settings() {
    }
}
