package securetransfer.common.measurement;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

/** Читает циклы потока через macOS thread_selfcounts; доступность зависит от оборудования. */
public final class MacOsCpuCycleCounter implements CpuCycleCounter {
    /** Системный режим THSC_CPI: число инструкций и процессорных циклов. */
    private static final int THREAD_CYCLES_AND_INSTRUCTIONS = 1;

    /** Системные функции macOS, вызываемые через готовый адаптер JNA. */
    private final MacSystemLibrary systemLibrary;

    /** Буфер двух uint64_t: сначала инструкции, затем циклы. */
    private final Memory threadCounts = new Memory(2L * Long.BYTES);

    /** Подключает системную библиотеку macOS без сборки собственного JNI-адаптера. */
    MacOsCpuCycleCounter() {
        systemLibrary = Native.load("System", MacSystemLibrary.class);
    }

    /**
     * Читает накопленные аппаратные циклы текущего потока.
     *
     * @return число циклов, учитываемых ядром macOS
     */
    @Override
    public long readCurrentThreadCycles() {
        int readStatus = systemLibrary.thread_selfcounts(
                THREAD_CYCLES_AND_INSTRUCTIONS, threadCounts, new NativeLong(threadCounts.size())
        );
        if (readStatus != 0) {
            throw new UnsupportedOperationException(
                    "macOS не предоставила циклы потока (errno=" + Native.getLastError() + ")."
            );
        }
        long currentThreadCycles = threadCounts.getLong(Long.BYTES);
        if (currentThreadCycles == 0) {
            throw new UnsupportedOperationException("На этом Mac счётчик циклов потока недоступен.");
        }
        return currentThreadCycles;
    }

    /**
     * Возвращает название системного источника циклов.
     *
     * @return описание учёта циклов ядром macOS
     */
    @Override
    public String getDescription() {
        return "macOS thread_selfcounts: аппаратные циклы текущего потока";
    }

    /** Освобождает нативный буфер результата. */
    @Override
    public void close() {
        threadCounts.close();
    }

    /** Минимальное отображение системного ABI macOS для JNA. */
    public interface MacSystemLibrary extends Library {
        /**
         * Записывает счётчики потока в буфер, заданный системным ABI Apple.
         *
         * @param countsKind режим запрашиваемых счётчиков
         * @param destination адрес двух 64-битных значений
         * @param destinationSize размер буфера в байтах
         * @return ноль при успехе, ненулевое значение при ошибке
         */
        int thread_selfcounts(int countsKind, Pointer destination, NativeLong destinationSize);
    }
}
