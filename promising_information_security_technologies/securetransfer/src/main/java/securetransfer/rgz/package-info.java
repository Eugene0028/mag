/**
 * Сценарии расчётно-графической работы по передаче зашифрованных файлов по сети.
 *
 * <p>Пакет предназначен для сравнения потоковых шифров, получения ключа шифрования
 * из общего секрета, передачи файлов и измерения отдельных фаз обмена.
 * Потоковые шифры размещаются в {@code securetransfer.rgz.cipher}.
 * Сравнение четырёх шифров запускается через {@link securetransfer.rgz.CipherBenchmarkMain}.
 * Получение ключа размещается в {@code securetransfer.rgz.key}; его стоимость
 * измеряется через {@link securetransfer.rgz.KeyDerivationBenchmarkMain}.
 * Получатель и отправитель файлов запускаются через {@link securetransfer.rgz.FileReceiverMain}
 * и {@link securetransfer.rgz.FileSenderMain}.
 * Общие компоненты используются из {@code securetransfer.common}.</p>
 */
package securetransfer.rgz;
