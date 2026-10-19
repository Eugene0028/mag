package securetransfer.storage;

import securetransfer.core.GroupParameterGenerator;
import securetransfer.core.GroupParameters;
import securetransfer.core.GroupValidator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Подготовка, сохранение и загрузка открытых параметров группы в текстовом файле.
 *
 * <p>Поля p, q и g записываются как десятичные числа в формате ключ=значение.
 * После загрузки вызывающий код проверяет математические условия для нужного типа группы.</p>
 */
public final class GroupParameterFileStorage {
    /**
     * Служебный конструктор класса статических файловых операций.
     */
    private GroupParameterFileStorage() {
    }

    /**
     * Загружает параметры полной группы или генерирует и сохраняет их, если файла нет.
     *
     * <p>В обоих случаях проверяет параметры. Ошибка в существующем файле
     * прерывает выполнение, не заменяя сохранённые параметры новыми.</p>
     *
     * @param parametersFilePath путь к параметрам полной группы
     * @param modulusBitLength битовая длина p для генерации отсутствующих параметров
     * @return проверенные параметры полной группы
     * @throws IOException если файл не удалось прочитать или сохранить
     * @throws IllegalArgumentException если параметры или битовая длина некорректны
     */
    public static GroupParameters loadOrGenerateFullGroupParameters(
            Path parametersFilePath,
            int modulusBitLength
    ) throws IOException {
        if (Files.exists(parametersFilePath)) {
            GroupParameters loadedParameters = loadGroupParameters(parametersFilePath);
            GroupValidator.validateFullGroupParameters(loadedParameters);
            return loadedParameters;
        }

        GroupParameters generatedParameters =
                GroupParameterGenerator.generateFullGroupParameters(modulusBitLength);
        GroupValidator.validateFullGroupParameters(generatedParameters);
        saveGroupParameters(generatedParameters, parametersFilePath);
        return generatedParameters;
    }

    /**
     * Загружает параметры подгруппы или генерирует и сохраняет их, если файла нет.
     *
     * <p>В обоих случаях проверяет параметры. Битовые длины используются только при генерации.</p>
     *
     * @param parametersFilePath путь к параметрам подгруппы
     * @param modulusBitLength битовая длина p для генерации отсутствующих параметров
     * @param subgroupOrderBitLength битовая длина q для генерации отсутствующих параметров
     * @return проверенные параметры подгруппы
     * @throws IOException если файл не удалось прочитать или сохранить
     * @throws IllegalArgumentException если параметры или битовые длины некорректны
     */
    public static GroupParameters loadOrGenerateSubgroupParameters(
            Path parametersFilePath,
            int modulusBitLength,
            int subgroupOrderBitLength
    ) throws IOException {
        if (Files.exists(parametersFilePath)) {
            GroupParameters loadedParameters = loadGroupParameters(parametersFilePath);
            GroupValidator.validateSubgroupParameters(loadedParameters);
            return loadedParameters;
        }

        GroupParameters generatedParameters = GroupParameterGenerator.generateSubgroupParameters(
                modulusBitLength,
                subgroupOrderBitLength
        );
        GroupValidator.validateSubgroupParameters(generatedParameters);
        saveGroupParameters(generatedParameters, parametersFilePath);
        return generatedParameters;
    }

    /**
     * Сохраняет параметры p, q и g в файл UTF-8, создавая родительские каталоги.
     *
     * @param groupParameters    предварительно проверенные открытые параметры группы
     * @param parametersFilePath путь к файлу; существующее содержимое заменяется
     * @throws IOException если каталог или файл не удалось создать либо записать
     */
    public static void saveGroupParameters(
            GroupParameters groupParameters,
            Path parametersFilePath
    ) throws IOException {
        Properties storedParameters = new Properties();
        storedParameters.setProperty("p", groupParameters.primeModulus().toString());
        storedParameters.setProperty("q", groupParameters.subgroupOrder().toString());
        storedParameters.setProperty("g", groupParameters.generator().toString());

        Path parentDirectory = parametersFilePath.toAbsolutePath().getParent();
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        try (BufferedWriter parametersWriter = Files.newBufferedWriter(
                parametersFilePath,
                StandardCharsets.UTF_8
        )) {
            storedParameters.store(parametersWriter, "Group parameters: p, q, g");
        }

        System.out.println("Параметры группы сохранены: " + parametersFilePath.toAbsolutePath());
    }

    /**
     * Читает поля p, q и g из файла UTF-8 и преобразует их в BigInteger.
     *
     * @param parametersFilePath путь к ранее сохранённым параметрам
     * @return прочитанные параметры, требующие проверки валидатором
     * @throws IOException если файл недоступен, содержит неверный формат или поля p, q, g отсутствуют
     */
    public static GroupParameters loadGroupParameters(Path parametersFilePath) throws IOException {
        Properties storedParameters = new Properties();
        GroupParameters loadedGroupParameters;

        try {
            try (BufferedReader parametersReader = Files.newBufferedReader(
                    parametersFilePath,
                    StandardCharsets.UTF_8
            )) {
                storedParameters.load(parametersReader);
            }

            loadedGroupParameters = new GroupParameters(
                    new BigInteger(storedParameters.getProperty("p", "")),
                    new BigInteger(storedParameters.getProperty("q", "")),
                    new BigInteger(storedParameters.getProperty("g", ""))
            );
        } catch (IllegalArgumentException invalidFileFormatException) {
            throw new IOException(
                    "Файл должен содержать целые десятичные значения p, q, g: " + parametersFilePath,
                    invalidFileFormatException
            );
        }

        System.out.println("Параметры группы загружены: " + parametersFilePath.toAbsolutePath());
        return loadedGroupParameters;
    }
}
