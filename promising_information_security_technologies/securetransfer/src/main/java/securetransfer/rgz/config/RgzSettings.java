package securetransfer.rgz.config;

import securetransfer.common.config.ApplicationDataPaths;
import securetransfer.common.config.GroupGenerationSettings;
import securetransfer.common.measurement.BenchmarkSettings;
import securetransfer.rgz.cipher.StreamCipherAlgorithm;

import java.nio.file.Path;

/** Настройки приложения расчётно-графической работы по передаче файлов. */
public final class RgzSettings {
    /** Собственный каталог параметров, ключей, доверенных материалов и результатов РГЗ. */
    public static final ApplicationDataPaths DATA_PATHS = new ApplicationDataPaths(Path.of("data", "rgz"));

    /** Размеры группы для выработки ключа в приложении РГЗ: p — 3072 бита, q — 256 бит. */
    public static final GroupGenerationSettings KEY_AGREEMENT_GROUP_SETTINGS = new GroupGenerationSettings(3072, 256);

    /** Прогрев и повторения каждого этапа четырёх потоковых шифров. */
    public static final BenchmarkSettings CIPHER_BENCHMARK_SETTINGS = new BenchmarkSettings(2000, 100, 10);

    /** Размер одной порции при измерении генерации ключевого потока: 64 КиБ. */
    public static final int KEY_STREAM_BLOCK_LENGTH_BYTES = 64 * 1024;

    /** Прогрев и повторения при сравнении SHA3-256 с этапами выработки ключа MQV. */
    public static final BenchmarkSettings KEY_DERIVATION_BENCHMARK_SETTINGS = new BenchmarkSettings(1000, 100, 10);

    /** Длина ключа для сравнения; все четыре выбранных шифра используют 128 бит. */
    public static final int DERIVED_KEY_LENGTH_BYTES = 16;

    /** Шифр приложения: наименьшая медиана генерации потока в проведённом сравнении. */
    public static final StreamCipherAlgorithm FILE_CIPHER = StreamCipherAlgorithm.SOSEMANUK;

    /** Адрес получателя для запуска двух процессов на одном компьютере. */
    public static final String DEFAULT_RECEIVER_HOST = "127.0.0.1";

    /** Порт передачи файлов, отдельный от первой лабораторной. */
    public static final int FILE_TRANSFER_PORT = 6060;

    /** Максимальное ожидание установки TCP-соединения. */
    public static final int CONNECT_TIMEOUT_MILLISECONDS = 5000;

    /** Максимальный простой одного чтения при обмене и передаче файла. */
    public static final int READ_TIMEOUT_MILLISECONDS = 30000;

    /** Буфер обработки файла: 64 КиБ независимо от полного размера. */
    public static final int FILE_BUFFER_LENGTH_BYTES = 64 * 1024;

    /** Каталог публикации полностью принятых и проверенных файлов. */
    public static final Path RECEIVED_FILES_DIRECTORY = DATA_PATHS.rootDirectory().resolve("received");

    /** Постоянный журнал резервирования IV; значения не откатываются при ошибке передачи. */
    public static final Path IV_SEQUENCE_FILE = DATA_PATHS.rootDirectory().resolve("state/iv-sequence.log");

    /** Число прогревочных передач перед серией файлов; они сохраняются, но не входят в медианы. */
    public static final int TRANSFER_WARMUP_COUNT = 2;

    /** Число отдельных MQV-сеансов для каждого размера файла. */
    public static final int TRANSFER_REPETITIONS = 5;

    /** Размеры файлов локального эксперимента; 81 проверяет хвост блока Sosemanuk. */
    public static final java.util.List<Long> TRANSFER_FILE_SIZES = java.util.List.of(
            0L, 81L, 1024L, 65536L, 1048576L, 10485760L, 67108864L
    );

    /** Запрещает создание экземпляров класса настроек. */
    private RgzSettings() {
    }
}
