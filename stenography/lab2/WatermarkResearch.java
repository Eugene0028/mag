import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Пакетная исследовательская часть для задания 2.
 * Генерирует один и тот же бинарный логотип, внедряет его в изображения трех наборов
 * двумя подходами и сохраняет PSNR + проверку извлечения в CSV.
 */
public final class WatermarkResearch {

    private static final String OUT_ROOT = "research_out";
    private static final String KEY = "lab2-secret-key";
    private static final int LOGO_W = 256;
    private static final int LOGO_H = 256;

    private WatermarkResearch() {
    }

    public static void main(String[] args) throws IOException {
        Path base = Path.of("").toAbsolutePath();
        Path outRoot = base.resolve(OUT_ROOT);
        Files.createDirectories(outRoot);
        WatermarkEngine.AdaptiveVariant adaptiveVariant = args.length > 0
                ? parseAdaptiveVariant(args[0])
                : WatermarkEngine.AdaptiveVariant.GRADIENT;

        BufferedImage logo = createLogo(LOGO_W, LOGO_H);
        Path logoPath = outRoot.resolve("research_logo.bmp");
        ImageIO.write(logo, "bmp", logoPath.toFile());
        int[] logoBits = WatermarkEngine.linearizeLogoBits(logo);

        List<SetSpec> specs = List.of(
                new SetSpec("BOSSBASE", base.resolve("../container1"), List.of("1.bmp", "2.bmp", "3.bmp", "4.bmp", "5.bmp")),
                new SetSpec("MEDICAL", base.resolve("../container2"), List.of("med_0.bmp", "med_1.bmp", "med_2.bmp", "med_3.bmp", "med_10.bmp")),
                new SetSpec("OTHER", base.resolve("../container3"), List.of("sat_12.bmp", "sat_13.bmp", "sat_14.bmp", "sat_15.bmp", "sat_16.bmp"))
        );

        Path metricsCsv = outRoot.resolve("watermark_metrics.csv");
        Path methodSummaryCsv = outRoot.resolve("watermark_summary.csv");
        Path summaryCsv = outRoot.resolve("dataset_summary.csv");
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        try (BufferedWriter metrics = Files.newBufferedWriter(metricsCsv, StandardCharsets.UTF_8);
             BufferedWriter summary = Files.newBufferedWriter(summaryCsv, StandardCharsets.UTF_8)) {
            metrics.write("set;image;method;adaptive_variant;embedded_bits;capacity_bits;payload_ratio;PSNR_dB;bit_errors;checked_bits;ber_percent;stego_file;extracted_logo");
            metrics.newLine();
            summary.write("set;directory;bmp_count;images;logo;logo_bits;key;adaptive_variant");
            summary.newLine();

            for (SetSpec spec : specs) {
                int bmpCount = validateSet(spec);
                summary.write(String.format(Locale.ROOT, "%s;%s;%d;%s;%s;%d;%s;%s",
                        spec.name, spec.dir.normalize(), bmpCount, String.join(",", spec.images),
                        logoPath.getFileName(), logoBits.length, KEY, adaptiveVariant));
                summary.newLine();

                Path setOut = outRoot.resolve(safeName(spec.name));
                Path stegoOut = setOut.resolve("stego");
                Path extractedOut = setOut.resolve("extracted");
                Files.createDirectories(stegoOut);
                Files.createDirectories(extractedOut);

                for (String imageName : spec.images) {
                    BufferedImage cover = WatermarkEngine.readImage(spec.dir.resolve(imageName).toFile());
                    MethodStats lsbStats = writeMethodResult(metrics, spec.name, imageName, "LSB_KEY", null,
                            cover, logoBits, stegoOut, extractedOut);
                    addAggregate(aggregates, spec.name, "LSB_KEY", "-", lsbStats);
                    MethodStats adaptiveStats = writeMethodResult(metrics, spec.name, imageName, "ADAPTIVE",
                            adaptiveVariant, cover, logoBits, stegoOut, extractedOut);
                    addAggregate(aggregates, spec.name, "ADAPTIVE", adaptiveVariant.name(), adaptiveStats);
                }
            }
        }
        writeAggregates(methodSummaryCsv, aggregates);

        System.out.println("Готово: " + outRoot.toAbsolutePath());
        System.out.println("Логотип: " + logoPath);
        System.out.println("Таблицы: watermark_metrics.csv, watermark_summary.csv, dataset_summary.csv");
    }

    private static MethodStats writeMethodResult(BufferedWriter metrics, String setName, String imageName, String method,
                                                 WatermarkEngine.AdaptiveVariant variant, BufferedImage cover,
                                                 int[] logoBits, Path stegoOut, Path extractedOut) throws IOException {
        WatermarkEngine.EmbedResult embed;
        int[] extractedBits;
        String suffix;
        if ("LSB_KEY".equals(method)) {
            embed = WatermarkEngine.embedKeyLsb(cover, logoBits, KEY);
            extractedBits = WatermarkEngine.extractKeyLsb(embed.stego, KEY, embed.bitLength);
            suffix = "lsb";
        } else {
            embed = WatermarkEngine.embedAdaptive(cover, logoBits, variant);
            extractedBits = WatermarkEngine.extractAdaptive(cover, embed.stego, embed.bitLength, variant);
            suffix = "adaptive_" + variant.name().toLowerCase(Locale.ROOT);
        }

        int checkedBits = Math.min(logoBits.length, extractedBits.length);
        int errors = WatermarkEngine.bitErrors(extractedBits, logoBits, checkedBits);
        double ber = checkedBits == 0 ? 0.0 : 100.0 * errors / checkedBits;
        double psnr = WatermarkEngine.psnr(cover, embed.stego);
        int capacity = cover.getWidth() * cover.getHeight();
        double payloadRatio = embed.bitLength / (double) capacity;

        String base = safeName(baseName(imageName));
        String stegoName = base + "_" + suffix + "_stego.bmp";
        String extractedName = base + "_" + suffix + "_logo.bmp";
        ImageIO.write(embed.stego, "bmp", stegoOut.resolve(stegoName).toFile());
        ImageIO.write(WatermarkEngine.bitsToLogoBitmap(extractedBits, LOGO_W, LOGO_H),
                "bmp", extractedOut.resolve(extractedName).toFile());

        metrics.write(String.format(Locale.ROOT,
                "%s;%s;%s;%s;%d;%d;%.6f;%.6f;%d;%d;%.6f;%s;%s",
                setName, imageName, method, variant == null ? "-" : variant.name(),
                embed.bitLength, capacity, payloadRatio, psnr, errors, checkedBits, ber, stegoName, extractedName));
        metrics.newLine();
        return new MethodStats(psnr, ber);
    }

    private static int validateSet(SetSpec spec) throws IOException {
        if (!Files.isDirectory(spec.dir)) {
            throw new IOException("Нет каталога набора: " + spec.dir);
        }
        int bmpCount = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(spec.dir, "*.bmp")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    bmpCount++;
                }
            }
        }
        if (bmpCount < 100) {
            throw new IOException("В наборе " + spec.name + " только " + bmpCount + " BMP, нужно не меньше 100.");
        }
        Set<String> unique = new HashSet<>();
        for (String image : spec.images) {
            if (!unique.add(image)) {
                throw new IOException("Повтор изображения в наборе " + spec.name + ": " + image);
            }
            Path path = spec.dir.resolve(image);
            if (!Files.isRegularFile(path)) {
                throw new IOException("Нет файла: " + path);
            }
            BufferedImage img = WatermarkEngine.readImage(path.toFile());
            if (img.getWidth() != 512 || img.getHeight() != 512) {
                throw new IOException("Изображение должно быть 512x512: " + path);
            }
        }
        return bmpCount;
    }

    private static WatermarkEngine.AdaptiveVariant parseAdaptiveVariant(String raw) throws IOException {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        switch (v) {
            case "1":
            case "gradient":
            case "grad":
                return WatermarkEngine.AdaptiveVariant.GRADIENT;
            case "2":
            case "variance":
            case "var":
                return WatermarkEngine.AdaptiveVariant.VARIANCE;
            case "3":
            case "contrast":
                return WatermarkEngine.AdaptiveVariant.CONTRAST;
            default:
                throw new IOException("Адаптивный вариант должен быть 1/gradient, 2/variance или 3/contrast.");
        }
    }

    private static void addAggregate(Map<String, Aggregate> aggregates, String set, String method, String variant,
                                     MethodStats stats) {
        String key = set + ";" + method + ";" + variant;
        Aggregate aggregate = aggregates.get(key);
        if (aggregate == null) {
            aggregate = new Aggregate(set, method, variant);
            aggregates.put(key, aggregate);
        }
        aggregate.add(stats);
    }

    private static void writeAggregates(Path csv, Map<String, Aggregate> aggregates) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            out.write("set;method;adaptive_variant;images;avg_PSNR_dB;min_PSNR_dB;max_PSNR_dB;avg_BER_percent");
            out.newLine();
            for (Aggregate aggregate : aggregates.values()) {
                out.write(String.format(Locale.ROOT, "%s;%s;%s;%d;%.6f;%.6f;%.6f;%.6f",
                        aggregate.set, aggregate.method, aggregate.variant, aggregate.count,
                        aggregate.psnrSum / aggregate.count, aggregate.psnrMin, aggregate.psnrMax,
                        aggregate.berSum / aggregate.count));
                out.newLine();
            }
        }
    }

    private static BufferedImage createLogo(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        for (int y = 0; y < h; y += 16) {
            g.fillRect(0, y, w, 8);
        }
        g.setColor(Color.WHITE);
        g.fillOval(24, 24, w - 48, h - 48);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 72));
        g.drawString("MAG", 42, 145);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        g.drawString("WATERMARK", 40, 188);
        g.dispose();
        return WatermarkEngine.binarizeLogo(img);
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String safeName(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private static final class SetSpec {
        final String name;
        final Path dir;
        final List<String> images;

        SetSpec(String name, Path dir, List<String> images) {
            this.name = name;
            this.dir = dir;
            this.images = new ArrayList<>(images);
        }
    }

    private static final class MethodStats {
        final double psnr;
        final double ber;

        MethodStats(double psnr, double ber) {
            this.psnr = psnr;
            this.ber = ber;
        }
    }

    private static final class Aggregate {
        final String set;
        final String method;
        final String variant;
        int count;
        double psnrSum;
        double psnrMin = Double.POSITIVE_INFINITY;
        double psnrMax = Double.NEGATIVE_INFINITY;
        double berSum;

        Aggregate(String set, String method, String variant) {
            this.set = set;
            this.method = method;
            this.variant = variant;
        }

        void add(MethodStats stats) {
            count++;
            psnrSum += stats.psnr;
            psnrMin = Math.min(psnrMin, stats.psnr);
            psnrMax = Math.max(psnrMax, stats.psnr);
            berSum += stats.ber;
        }
    }
}
