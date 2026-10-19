package securetransfer.common.measurement;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Читает циклы текущего потока через Windows QueryThreadCycleTime.
 * Трактовка аппаратных тактов этим API зависит от процессора.
 */
public final class WindowsCpuCycleCounter implements CpuCycleCounter {
    /** Функции системной библиотеки Kernel32. */
    private final WindowsSystemLibrary systemLibrary;

    /** Буфер беззнакового 64-битного результата Windows. */
    private final LongByReference threadCycles = new LongByReference();

    /** Подключает функции Windows через библиотеку JNA подходящей архитектуры. */
    WindowsCpuCycleCounter() {
        systemLibrary = Native.load("kernel32", WindowsSystemLibrary.class);
    }

    /**
     * Читает циклы потока в пользовательском режиме и режиме ядра.
     *
     * @return число циклов, возвращённое Windows
     */
    @Override
    public long readCurrentThreadCycles() {
        int readSucceeded = systemLibrary.QueryThreadCycleTime(systemLibrary.GetCurrentThread(), threadCycles);
        if (readSucceeded == 0) {
            throw new UnsupportedOperationException(
                    "Windows не предоставила циклы потока (код ошибки=" + Native.getLastError() + ")."
            );
        }
        return threadCycles.getValue();
    }

    /**
     * Описывает источник и область учёта циклов Windows.
     *
     * @return описание системного счётчика
     */
    @Override
    public String getDescription() {
        return "Windows QueryThreadCycleTime: циклы потока, включая режим ядра; единица зависит от CPU";
    }

    /** Минимальное отображение системного ABI Windows для JNA. */
    public interface WindowsSystemLibrary extends StdCallLibrary {
        /**
         * Возвращает псевдодескриптор текущего потока, не требующий закрытия.
         *
         * @return псевдодескриптор потока
         */
        Pointer GetCurrentThread();

        /**
         * Записывает накопленные циклы указанного потока.
         *
         * @param threadHandle дескриптор потока
         * @param cycleCount буфер результата типа ULONG64
         * @return ненулевое значение при успехе
         */
        int QueryThreadCycleTime(Pointer threadHandle, LongByReference cycleCount);
    }
}
