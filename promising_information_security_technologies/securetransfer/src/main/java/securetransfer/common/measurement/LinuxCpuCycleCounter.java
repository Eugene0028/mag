package securetransfer.common.measurement;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

/**
 * Считает аппаратные циклы пользовательского кода текущего потока через Linux perf_event_open.
 * Системные ограничения perf не меняются; при запрете возвращается понятная ошибка.
 */
public final class LinuxCpuCycleCounter implements CpuCycleCounter {
    /** Размер первого стабильного ABI perf_event_attr: PERF_ATTR_SIZE_VER0. */
    private static final int PERFORMANCE_EVENT_ATTRIBUTES_SIZE = 64;

    /** Функции стандартной C-библиотеки Linux. */
    private final LinuxSystemLibrary systemLibrary;

    /** Поток, для которого открыт системный счётчик. */
    private final Thread ownerThread = Thread.currentThread();

    /** Результат read: циклы, время включения и время фактического учёта. */
    private final Memory counterValues = new Memory(3L * Long.BYTES);

    /** Дескриптор perf; значение -1 обозначает закрытый счётчик. */
    private int counterFileDescriptor;

    /**
     * Открывает аппаратный счётчик вызывающего потока на всех процессорных ядрах.
     *
     * @param processorArchitecture архитектура JVM: x86-64 либо ARM64
     */
    LinuxCpuCycleCounter(String processorArchitecture) {
        systemLibrary = Native.load("c", LinuxSystemLibrary.class);
        // Номера syscall из ABI Linux: x86-64 — 298, AArch64 — 241.
        long openEventSystemCall = processorArchitecture.equals("amd64")
                || processorArchitecture.equals("x86_64") ? 298 : 241;
        try (Memory eventAttributes = new Memory(PERFORMANCE_EVENT_ATTRIBUTES_SIZE)) {
            eventAttributes.clear();
            eventAttributes.setInt(0, 0); // PERF_TYPE_HARDWARE.
            eventAttributes.setInt(4, PERFORMANCE_EVENT_ATTRIBUTES_SIZE);
            eventAttributes.setLong(8, 0); // PERF_COUNT_HW_CPU_CYCLES.
            eventAttributes.setLong(32, 3); // PERF_FORMAT_TOTAL_TIME_ENABLED | TOTAL_TIME_RUNNING.
            // pinned: не подменять недоступные циклы оценкой при мультиплексировании.
            // exclude_kernel и exclude_hv: учитывать только пользовательский код.
            eventAttributes.setLong(40, (1L << 2) | (1L << 5) | (1L << 6));
            counterFileDescriptor = systemLibrary.syscall(
                    new NativeLong(openEventSystemCall), eventAttributes,
                    new NativeLong(0), new NativeLong(-1), new NativeLong(-1), new NativeLong(8)
            ).intValue(); // PERF_FLAG_FD_CLOEXEC = 8.
        }
        if (counterFileDescriptor < 0) {
            int systemErrorNumber = Native.getLastError();
            counterValues.close();
            throw new UnsupportedOperationException(
                    "Linux не разрешил открыть счётчик CPU (errno=" + systemErrorNumber
                            + "). Возможны ограничения perf, виртуальной машины или оборудования."
            );
        }
    }

    /**
     * Читает циклы потока и отклоняет неполный аппаратный учёт.
     *
     * @return число реально учтённых аппаратных циклов пользовательского кода
     */
    @Override
    public long readCurrentThreadCycles() {
        if (Thread.currentThread() != ownerThread || counterFileDescriptor < 0) {
            throw new IllegalStateException("Счётчик нужно читать в создавшем его потоке до закрытия.");
        }
        long readByteCount = systemLibrary.read(
                counterFileDescriptor, counterValues, new NativeLong(counterValues.size())
        ).longValue();
        if (readByteCount != counterValues.size()) {
            throw new UnsupportedOperationException("Linux не предоставил полный результат аппаратного счётчика.");
        }
        long enabledTime = counterValues.getLong(Long.BYTES);
        long runningTime = counterValues.getLong(2L * Long.BYTES);
        if (runningTime == 0 || runningTime != enabledTime) {
            throw new UnsupportedOperationException("Аппаратный счётчик Linux не учитывал весь интервал.");
        }
        return counterValues.getLong(0);
    }

    /**
     * Указывает, что циклы ядра и гипервизора исключены.
     *
     * @return описание области аппаратного учёта
     */
    @Override
    public String getDescription() {
        return "Linux perf_event_open: аппаратные циклы потока в пользовательском режиме";
    }

    /** Закрывает дескриптор perf и освобождает буфер результата. */
    @Override
    public void close() {
        if (counterFileDescriptor >= 0) {
            systemLibrary.close(counterFileDescriptor);
            counterFileDescriptor = -1;
            counterValues.close();
        }
    }

    /** Минимальное отображение 64-битного ABI Linux для JNA. */
    public interface LinuxSystemLibrary extends Library {
        /**
         * Выполняет perf_event_open; аргументы syscall передаются машинными словами.
         *
         * @param number номер системного вызова для архитектуры JVM
         * @param attributes структура perf_event_attr
         * @param processId ноль для вызывающего потока
         * @param cpuNumber -1 для любого ядра
         * @param groupDescriptor -1 для отдельного счётчика
         * @param flags флаги открытия дескриптора
         * @return дескриптор perf либо -1 при ошибке
         */
        NativeLong syscall(NativeLong number, Pointer attributes, NativeLong processId,
                           NativeLong cpuNumber, NativeLong groupDescriptor, NativeLong flags);

        /**
         * Читает значения открытого системного счётчика.
         *
         * @param descriptor дескриптор perf
         * @param destination буфер результата
         * @param byteCount размер буфера
         * @return число прочитанных байтов либо -1 при ошибке
         */
        NativeLong read(int descriptor, Pointer destination, NativeLong byteCount);

        /**
         * Освобождает системный дескриптор.
         *
         * @param descriptor дескриптор perf
         * @return ноль при успехе либо -1 при ошибке
         */
        int close(int descriptor);
    }
}
