package securetransfer.rgz.cipher;

/**
 * Дополнительные операции шифров с отдельной подготовкой ключа и IV.
 *
 * <p>Такой API доступен у Rabbit и Sosemanuk. После {@link #setupKey(byte[])}
 * необходимо вызвать {@link #setupInitializationVector(byte[])}. Повторная
 * установка IV использует сохранённое состояние ключа и начинает новый поток.</p>
 */
public interface KeyAndIvSetupCipher extends StreamCipher {
    /**
     * Выполняет развёртку ключа и сохраняет результат для последующей установки IV.
     * Обработка данных до установки IV запрещена.
     *
     * @param secretKey ключ требуемой длины
     * @throws NullPointerException если ключ равен {@code null}
     * @throws IllegalArgumentException если длина ключа неверна
     */
    void setupKey(byte[] secretKey);

    /**
     * Подготавливает начало нового потока из сохранённого состояния ключа и IV.
     *
     * @param initializationVector IV требуемой длины
     * @throws IllegalStateException если ключ ещё не подготовлен
     * @throws NullPointerException если IV равен {@code null}
     * @throws IllegalArgumentException если длина IV неверна
     */
    void setupInitializationVector(byte[] initializationVector);
}
