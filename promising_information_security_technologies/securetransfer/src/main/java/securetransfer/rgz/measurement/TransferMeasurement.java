package securetransfer.rgz.measurement;

import java.util.EnumMap;

/** Замеры одного конца одного соединения; экземпляр используется только одним потоком. */
public final class TransferMeasurement {
    /** Накопленные наносекунды по фазам. */
    private final EnumMap<TransferPhase, Long> durations = new EnumMap<>(TransferPhase.class);

    /** Безопасное имя передаваемого файла для сопоставления CSV двух компьютеров. */
    private String fileName = "";

    /** Заявленный размер файла. */
    private long fileSizeBytes;

    /**
     * Запоминает метаданные файла; содержимое и секреты не сохраняются.
     * @param fileName имя файла
     * @param fileSizeBytes размер в байтах
     */
    public void identifyFile(String fileName, long fileSizeBytes) {
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
    }

    /**
     * Добавляет прошедшее время с момента начала операции.
     * @param phase измеряемая фаза
     * @param startedAtNanoseconds значение System.nanoTime перед операцией
     */
    public void finishInterval(TransferPhase phase, long startedAtNanoseconds) {
        durations.merge(phase, System.nanoTime() - startedAtNanoseconds, Long::sum);
    }

    /** @return имя переданного файла */
    public String fileName() {
        return fileName;
    }

    /** @return размер переданного файла */
    public long fileSizeBytes() {
        return fileSizeBytes;
    }

    /**
     * Возвращает сумму завершённых интервалов одной фазы.
     * @param phase фаза
     * @return наносекунды; ноль, если фаза на этой стороне отсутствует
     */
    public long nanoseconds(TransferPhase phase) {
        return durations.getOrDefault(phase, 0L);
    }
}
