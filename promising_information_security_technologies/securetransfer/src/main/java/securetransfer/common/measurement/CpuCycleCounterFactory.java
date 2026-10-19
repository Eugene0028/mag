package securetransfer.common.measurement;

import java.util.Locale;

/**
 * Выбирает системную реализацию счётчика по ОС и архитектуре запущенной JVM.
 *
 * <p>Совместимый нативный адаптер внутри JNA выбирает сама JNA.
 * Криптографическим алгоритмам создание счётчика не требуется.</p>
 */
public final class CpuCycleCounterFactory {
    /** Запрещает создание экземпляров фабрики со статическими методами. */
    private CpuCycleCounterFactory() {
    }

    /**
     * Создаёт счётчик для macOS, Windows или Linux на x86-64 либо ARM64.
     *
     * @return счётчик для вызывающего потока
     * @throws UnsupportedOperationException если платформа или системный счётчик недоступны
     * @throws LinkageError если ОС не позволила загрузить адаптер JNA
     */
    public static CpuCycleCounter createForCurrentPlatform() {
        String processorArchitecture = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (!(processorArchitecture.equals("amd64") || processorArchitecture.equals("x86_64")
                || processorArchitecture.equals("aarch64") || processorArchitecture.equals("arm64"))) {
            throw new UnsupportedOperationException("Не поддерживается архитектура JVM: " + processorArchitecture);
        }

        String operatingSystemName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (operatingSystemName.contains("mac")) {
            return new MacOsCpuCycleCounter();
        }
        if (operatingSystemName.startsWith("windows")) {
            return new WindowsCpuCycleCounter();
        }
        if (operatingSystemName.equals("linux")) {
            return new LinuxCpuCycleCounter(processorArchitecture);
        }
        throw new UnsupportedOperationException("Не поддерживается ОС: " + operatingSystemName);
    }
}
