package securetransfer.common.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Пути рабочих файлов одного приложения относительно переданного корневого каталога.
 *
 * <p>Лабораторная работа и РГЗ передают собственные каталоги данных.
 * Создание каталогов выполняется файловым хранилищем при сохранении.</p>
 *
 * @param rootDirectory корневой каталог данных конкретного приложения
 */
public record ApplicationDataPaths(Path rootDirectory) {
    /** Проверяет наличие пути и убирает из него избыточные элементы. */
    public ApplicationDataPaths {
        rootDirectory = Objects.requireNonNull(rootDirectory).normalize();
    }

    /** @return каталог открытых параметров групп */
    public Path parametersDirectory() {
        return rootDirectory.resolve("params");
    }

    /** @return каталог параметров, используемых при измерениях */
    public Path benchmarkParametersDirectory() {
        return parametersDirectory().resolve("benchmark");
    }

    /** @return каталог результатов отдельных запусков измерений */
    public Path reportsDirectory() {
        return rootDirectory.resolve("reports");
    }

    /** @return файл параметров полной мультипликативной группы */
    public Path fullGroupParametersFile() {
        return parametersDirectory().resolve("full-group.properties");
    }

    /** @return файл параметров подгруппы простого порядка */
    public Path subgroupParametersFile() {
        return parametersDirectory().resolve("subgroup.properties");
    }

    /** @return файл долговременной пары Алисы */
    public Path aliceLongTermKeyFile() {
        return rootDirectory.resolve("keys").resolve("alice-long-term.properties");
    }

    /** @return файл долговременной пары Боба */
    public Path bobLongTermKeyFile() {
        return rootDirectory.resolve("keys").resolve("bob-long-term.properties");
    }

    /** @return файл подписанного справочника открытых ключей MQV */
    public Path signedDirectoryFile() {
        return rootDirectory.resolve("trust").resolve("mqv-directory.properties");
    }

    /** @return файл доверенного открытого ключа центра */
    public Path authorityPublicKeyFile() {
        return rootDirectory.resolve("trust").resolve("authority-public.der");
    }
}
