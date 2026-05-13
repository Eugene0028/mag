import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import javax.imageio.ImageIO;

/**
 * Задание 2: ЦВЗ — LSB с ключом; адаптивно (градиент / дисперсия / контраст); извлечение и PSNR.
 */
public final class WatermarkLab {

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8)) {
            System.out.println("Задание 2 — цифровой водяной знак");
            System.out.println("1 — внедрить (LSB + секретный ключ)");
            System.out.println("2 — внедрить (адаптивно: градиент / дисперсия / контраст)");
            System.out.println("3 — извлечь (LSB + ключ)");
            System.out.println("4 — извлечь (адаптивно, нужен исходный контейнер + тот же вариант)");
            System.out.println("5 — PSNR: контейнер vs стего");
            System.out.print("Режим (1-5): ");
            int mode = readIntLine(sc, "ожидалось число 1..5");

            switch (mode) {
                case 1:
                    runEmbedKey(sc);
                    break;
                case 2:
                    runEmbedAdaptive(sc);
                    break;
                case 3:
                    runExtractKey(sc);
                    break;
                case 4:
                    runExtractAdaptive(sc);
                    break;
                case 5:
                    runPsnr(sc);
                    break;
                default:
                    System.out.println("Неизвестный режим.");
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) {
                msg = e.toString();
            }
            System.err.println("Ошибка: " + msg);
        }
    }

    private static int readIntLine(Scanner sc, String onBad) throws IOException {
        String line = sc.nextLine().trim();
        if (line.isEmpty()) {
            throw new IOException("Пустой ввод. " + onBad);
        }
        try {
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            throw new IOException(onBad + ", получено: " + line);
        }
    }

    private static void runEmbedKey(Scanner sc) throws IOException {
        System.out.print("Путь к контейнеру (BMP/PNG): ");
        String coverPath = sc.nextLine().trim();
        System.out.print("Путь к логотипу (изображение): ");
        String logoPath = sc.nextLine().trim();
        System.out.print("Секретный ключ (строка): ");
        String key = sc.nextLine();
        System.out.print("Выходной BMP (например lab2_stego_key.bmp): ");
        String outPath = sc.nextLine().trim();

        BufferedImage cover = WatermarkEngine.readImage(new File(coverPath));
        BufferedImage logoRaw = WatermarkEngine.readImage(new File(logoPath));
        logoRaw = WatermarkEngine.scaleIfNeeded(logoRaw, cover.getWidth() * cover.getHeight());
        BufferedImage logoBin = WatermarkEngine.binarizeLogo(logoRaw);
        int[] bits = WatermarkEngine.linearizeLogoBits(logoBin);

        WatermarkEngine.EmbedResult res = WatermarkEngine.embedKeyLsb(cover, bits, key);
        ImageIO.write(res.stego, "bmp", new File(outPath));
        double psnr = WatermarkEngine.psnr(cover, res.stego);
        System.out.println("Внедрено бит: " + res.bitLength + " (ёмкость плоскости " + cover.getWidth() * cover.getHeight()
                + ", логотип " + logoBin.getWidth() + "×" + logoBin.getHeight() + " = " + bits.length + " бит)");
        System.out.println("PSNR(контейнер, стего) = " + String.format("%.4f", psnr) + " дБ");
        System.out.println("Сохранено: " + outPath);
        System.out.println("Для извлечения укажите те же размеры логотипа: " + logoBin.getWidth() + " " + logoBin.getHeight());
    }

    private static void runEmbedAdaptive(Scanner sc) throws IOException {
        System.out.print("Путь к контейнеру (BMP/PNG): ");
        String coverPath = sc.nextLine().trim();
        System.out.print("Путь к логотипу (изображение): ");
        String logoPath = sc.nextLine().trim();
        WatermarkEngine.AdaptiveVariant variant = readAdaptiveVariant(sc);
        System.out.print("Выходной BMP (например lab2_stego_adapt.bmp): ");
        String outPath = sc.nextLine().trim();

        BufferedImage cover = WatermarkEngine.readImage(new File(coverPath));
        BufferedImage logoRaw = WatermarkEngine.readImage(new File(logoPath));
        logoRaw = WatermarkEngine.scaleIfNeeded(logoRaw, cover.getWidth() * cover.getHeight());
        BufferedImage logoBin = WatermarkEngine.binarizeLogo(logoRaw);
        int[] bits = WatermarkEngine.linearizeLogoBits(logoBin);

        WatermarkEngine.EmbedResult res = WatermarkEngine.embedAdaptive(cover, bits, variant);
        ImageIO.write(res.stego, "bmp", new File(outPath));
        double psnr = WatermarkEngine.psnr(cover, res.stego);
        System.out.println("Вариант: " + variant);
        System.out.println("Внедрено бит: " + res.bitLength);
        System.out.println("PSNR(контейнер, стего) = " + String.format("%.4f", psnr) + " дБ");
        System.out.println("Сохранено: " + outPath);
        System.out.println("Размеры логотипа для проверки: " + logoBin.getWidth() + " " + logoBin.getHeight());
    }

    private static void runExtractKey(Scanner sc) throws IOException {
        System.out.print("Путь к стего BMP: ");
        String stegoPath = sc.nextLine().trim();
        System.out.print("Секретный ключ: ");
        String key = sc.nextLine();
        System.out.print("Ширина логотипа (px): ");
        int lw = readIntLine(sc, "нужна ширина в пикселях");
        System.out.print("Высота логотипа (px): ");
        int lh = readIntLine(sc, "нужна высота в пикселях");
        System.out.print("Выходной извлечённый BMP (например lab2_logo_out.bmp): ");
        String outPath = sc.nextLine().trim();
        System.out.print("Путь к эталонному логотипу для сравнения (Enter — пропустить): ");
        String refPath = sc.nextLine().trim();

        BufferedImage stego = WatermarkEngine.readImage(new File(stegoPath));
        int w = stego.getWidth();
        int h = stego.getHeight();
        int L = WatermarkEngine.embedBitLength(w, h, lw * lh);
        int[] bits = WatermarkEngine.extractKeyLsb(stego, key, L);
        BufferedImage recovered = WatermarkEngine.bitsToLogoBitmap(bits, lw, lh);
        ImageIO.write(recovered, "bmp", new File(outPath));

        if (!refPath.isEmpty()) {
            BufferedImage ref = WatermarkEngine.binarizeLogo(WatermarkEngine.readImage(new File(refPath)));
            ref = WatermarkEngine.scaleIfNeeded(ref, lw * lh);
            if (ref.getWidth() != lw || ref.getHeight() != lh) {
                System.out.println("Предупреждение: размер эталона не совпадает, масштаб не применялся к точным lw×lh.");
            }
            int[] refBits = WatermarkEngine.linearizeLogoBits(ref);
            int err = WatermarkEngine.bitErrors(bits, refBits, Math.min(lw * lh, Math.min(bits.length, refBits.length)));
            int n = lw * lh;
            System.out.println("Ошибок по битам (первый период логотипа): " + err + " из " + n
                    + " (" + String.format("%.4f", 100.0 * err / n) + "%)");
        }
        System.out.println("Извлечено в: " + outPath);
    }

    private static void runExtractAdaptive(Scanner sc) throws IOException {
        System.out.print("Путь к исходному контейнеру (как при внедрении): ");
        String coverPath = sc.nextLine().trim();
        System.out.print("Путь к стего BMP: ");
        String stegoPath = sc.nextLine().trim();
        WatermarkEngine.AdaptiveVariant variant = readAdaptiveVariant(sc);
        System.out.print("Ширина логотипа (px): ");
        int lw = readIntLine(sc, "нужна ширина в пикселях");
        System.out.print("Высота логотипа (px): ");
        int lh = readIntLine(sc, "нужна высота в пикселях");
        System.out.print("Выходной BMP: ");
        String outPath = sc.nextLine().trim();
        System.out.print("Эталонный логотип для сравнения (Enter — пропустить): ");
        String refPath = sc.nextLine().trim();

        BufferedImage cover = WatermarkEngine.readImage(new File(coverPath));
        BufferedImage stego = WatermarkEngine.readImage(new File(stegoPath));
        int w = stego.getWidth();
        int h = stego.getHeight();
        if (cover.getWidth() != w || cover.getHeight() != h) {
            throw new IOException("Размеры контейнера и стего должны совпадать.");
        }
        int L = WatermarkEngine.embedBitLength(w, h, lw * lh);
        int[] bits = WatermarkEngine.extractAdaptive(cover, stego, L, variant);
        BufferedImage recovered = WatermarkEngine.bitsToLogoBitmap(bits, lw, lh);
        ImageIO.write(recovered, "bmp", new File(outPath));

        System.out.println("Вариант: " + variant);
        if (!refPath.isEmpty()) {
            BufferedImage ref = WatermarkEngine.binarizeLogo(WatermarkEngine.readImage(new File(refPath)));
            int[] refBits = WatermarkEngine.linearizeLogoBits(ref);
            int n = Math.min(lw * lh, Math.min(bits.length, refBits.length));
            int err = WatermarkEngine.bitErrors(bits, refBits, n);
            System.out.println("Ошибок по битам: " + err + " из " + (lw * lh));
        }
        System.out.println("Сохранено: " + outPath);
    }

    private static void runPsnr(Scanner sc) throws IOException {
        System.out.print("Путь к исходному контейнеру: ");
        String aPath = sc.nextLine().trim();
        System.out.print("Путь к стего: ");
        String bPath = sc.nextLine().trim();
        BufferedImage a = WatermarkEngine.readImage(new File(aPath));
        BufferedImage b = WatermarkEngine.readImage(new File(bPath));
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            throw new IOException("Размеры изображений различаются.");
        }
        double p = WatermarkEngine.psnr(a, b);
        System.out.println("PSNR = " + String.format("%.4f", p) + " дБ");
    }

    private static WatermarkEngine.AdaptiveVariant readAdaptiveVariant(Scanner sc) throws IOException {
        System.out.println("Вариант адаптации (окно 3×3): 1 — градиент, 2 — дисперсия, 3 — контраст (max−min)");
        System.out.print("Введите 1, 2 или 3: ");
        int v = readIntLine(sc, "ожидалось 1, 2 или 3");
        switch (v) {
            case 1:
                return WatermarkEngine.AdaptiveVariant.GRADIENT;
            case 2:
                return WatermarkEngine.AdaptiveVariant.VARIANCE;
            case 3:
                return WatermarkEngine.AdaptiveVariant.CONTRAST;
            default:
                throw new IOException("Ожидалось 1, 2 или 3");
        }
    }
}
