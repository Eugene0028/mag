import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Исследовательская часть: пакетная визуализация битовых плоскостей, внедрение при k=1..3,
 * MSE, PSNR, SSIM, гистограммы и CSV для таблиц сравнения.
 * <p>
 * Запуск из каталога stenography (или укажите абсолютные пути в конфиге):
 * {@code javac StegoResearch.java && java StegoResearch}
 * <p>
 * По умолчанию читается {@code research_config.txt} в текущей директории.
 * Первая непустая строка (не комментарий #) — путь к файлу сообщения.
 * Далее строки: ИМЯ_НАБОРА|КАТАЛОГ|f1.bmp,...,f5.bmp|файл_для_внедрения.bmp
 */
public final class StegoResearch {

    private static final String DEFAULT_CONFIG = "research_config.txt";
    private static final String OUT_ROOT = "research_out";

    private static final double L = 255.0;
    private static final double K1 = 0.01;
    private static final double K2 = 0.03;
    private static final double C1 = (K1 * L) * (K1 * L);
    private static final double C2 = (K2 * L) * (K2 * L);
    private static final int SSIM_WIN = 7;
    private static final int SSIM_R = SSIM_WIN / 2;

    public static void main(String[] args) throws IOException {
        Path cwd = Path.of("").toAbsolutePath();
        Path configPath = cwd.resolve(args.length > 0 ? args[0] : DEFAULT_CONFIG);
        if (!Files.isRegularFile(configPath)) {
            System.err.println("Нет файла конфигурации: " + configPath);
            System.err.println("Создайте " + DEFAULT_CONFIG + " по образцу из репозитория.");
            return;
        }
        List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
        List<String> effective = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            effective.add(t);
        }
        if (effective.size() < 4) {
            throw new IOException("В конфиге нужны: сообщение + 3 строки наборов.");
        }
        Path msgPath = cwd.resolve(effective.get(0));
        byte[] message = Files.readAllBytes(msgPath);

        Path outRoot = cwd.resolve(OUT_ROOT);
        Files.createDirectories(outRoot);

        Path metricsCsv = outRoot.resolve("metrics_all.csv");
        Path entropyCsv = outRoot.resolve("bitplane_entropy_all.csv");

        try (BufferedWriter mw = Files.newBufferedWriter(metricsCsv, StandardCharsets.UTF_8);
             BufferedWriter ew = Files.newBufferedWriter(entropyCsv, StandardCharsets.UTF_8)) {
            mw.write("set;rep_image;k;MSE;PSNR_dB;SSIM");
            mw.newLine();
            ew.write("set;image;k;p0;p1;entropy_bits");
            ew.newLine();

            for (int si = 1; si <= 3; si++) {
                SetSpec spec = SetSpec.parse(effective.get(si), cwd);
                System.out.println("Набор: " + spec.name + " -> " + spec.dir);

                Path setOut = outRoot.resolve(safeName(spec.name));
                Path planesRoot = setOut.resolve("planes");
                Path stegoRoot = setOut.resolve("stego");
                Path histRoot = setOut.resolve("hist");
                Path diffRoot = setOut.resolve("diff");
                Files.createDirectories(planesRoot);
                Files.createDirectories(stegoRoot);
                Files.createDirectories(histRoot);
                Files.createDirectories(diffRoot);

                for (String planeFile : spec.planeFiles) {
                    Path bmp = spec.dir.resolve(planeFile);
                    BufferedImage gray = readGray(bmp);
                    String base = baseName(planeFile);
                    Path oneImgPlanes = planesRoot.resolve("img_" + safeName(base));
                    Files.createDirectories(oneImgPlanes);
                    for (int k = 1; k <= 8; k++) {
                        BufferedImage plane = buildPlaneVisualization(gray, k);
                        Path outBmp = oneImgPlanes.resolve(String.format(Locale.ROOT, "plane_%02d.bmp", k));
                        ImageIO.write(plane, "bmp", outBmp.toFile());

                        BitEntropy be = bitEntropy(gray, k);
                        ew.write(String.format(Locale.ROOT, "%s;%s;%d;%.6f;%.6f;%.6f",
                                spec.name, planeFile, k, be.p0, be.p1, be.entropy));
                        ew.newLine();
                    }
                }

                Path repBmp = spec.dir.resolve(spec.repFile);
                BufferedImage original = readGray(repBmp);
                saveHistogram(histogram(original), histRoot.resolve("rep_original_hist.png"), spec.name + " original");

                for (int k = 1; k <= 3; k++) {
                    BufferedImage stego = embedMessage(copyGray(original), message, k);
                    String stegoName = String.format(Locale.ROOT, "rep_%s_k%d_stego.bmp", safeName(baseName(spec.repFile)), k);
                    ImageIO.write(stego, "bmp", stegoRoot.resolve(stegoName).toFile());

                    double mse = mse(original, stego);
                    double psnr = psnr(mse);
                    double ssim = ssimGray(original, stego);
                    mw.write(String.format(Locale.ROOT, "%s;%s;%d;%.8f;%.6f;%.8f",
                            spec.name, spec.repFile, k, mse, psnr, ssim));
                    mw.newLine();

                    saveHistogram(histogram(stego), histRoot.resolve("rep_k" + k + "_stego_hist.png"),
                            spec.name + " k=" + k);
                    saveAbsDiffScaled(original, stego, diffRoot.resolve("rep_k" + k + "_absdiff.png"));
                }
            }
        }
        System.out.println("Готово. Результаты в каталоге: " + outRoot.toAbsolutePath());
        System.out.println("Таблицы: metrics_all.csv, bitplane_entropy_all.csv");
    }

    private static final class SetSpec {
        final String name;
        final Path dir;
        final List<String> planeFiles;
        final String repFile;

        SetSpec(String name, Path dir, List<String> planeFiles, String repFile) {
            this.name = name;
            this.dir = dir;
            this.planeFiles = planeFiles;
            this.repFile = repFile;
        }

        static SetSpec parse(String line, Path cwd) {
            String[] parts = line.split("\\|");
            if (parts.length != 4) {
                throw new IllegalArgumentException("Строка набора должна быть: ИМЯ|КАТАЛОГ|f1,...,f5|rep.bmp — " + line);
            }
            String name = parts[0].trim();
            Path dir = cwd.resolve(parts[1].trim());
            String[] fs = parts[2].trim().split(",");
            if (fs.length != 5) {
                throw new IllegalArgumentException("Нужно ровно 5 файлов через запятую: " + line);
            }
            List<String> files = new ArrayList<>();
            for (String f : fs) {
                files.add(f.trim());
            }
            String rep = parts[3].trim();
            return new SetSpec(name, dir, files, rep);
        }
    }

    private static BufferedImage readGray(Path path) throws IOException {
        BufferedImage img = ImageIO.read(path.toFile());
        if (img == null) {
            throw new IOException("Не удалось прочитать: " + path);
        }
        return toByteGray(img);
    }

    private static BufferedImage toByteGray(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster dr = dst.getRaster();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int yv = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                if (yv < 0) {
                    yv = 0;
                } else if (yv > 255) {
                    yv = 255;
                }
                dr.setSample(x, y, 0, yv);
            }
        }
        return dst;
    }

    private static BufferedImage copyGray(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage c = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster sr = src.getRaster();
        WritableRaster dr = c.getRaster();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dr.setSample(x, y, 0, sr.getSample(x, y, 0));
            }
        }
        return c;
    }

    private static BufferedImage buildPlaneVisualization(BufferedImage gray, int k) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        int shift = k - 1;
        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster src = gray.getRaster();
        WritableRaster dst = res.getRaster();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = src.getSample(x, y, 0) & 0xFF;
                int bit = (v >> shift) & 1;
                dst.setSample(x, y, 0, bit == 1 ? 255 : 0);
            }
        }
        return res;
    }

    private static BufferedImage embedMessage(BufferedImage gray, byte[] message, int k) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        int capacity = w * h;
        WritableRaster r = gray.getRaster();
        int shift = k - 1;
        int pixelIdx = 0;
        outer:
        for (byte b : message) {
            for (int i = 7; i >= 0; i--) {
                if (pixelIdx >= capacity) {
                    break outer;
                }
                int x = pixelIdx % w;
                int y = pixelIdx / w;
                int v = r.getSample(x, y, 0) & 0xFF;
                int bit = (b >> i) & 1;
                v = (v & ~(1 << shift)) | (bit << shift);
                r.setSample(x, y, 0, v);
                pixelIdx++;
            }
        }
        return gray;
    }

    private static final class BitEntropy {
        final double p0;
        final double p1;
        final double entropy;

        BitEntropy(double p0, double p1, double entropy) {
            this.p0 = p0;
            this.p1 = p1;
            this.entropy = entropy;
        }
    }

    private static BitEntropy bitEntropy(BufferedImage gray, int k) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        long n = (long) w * h;
        WritableRaster r = gray.getRaster();
        int shift = k - 1;
        long c0 = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = r.getSample(x, y, 0) & 0xFF;
                int bit = (v >> shift) & 1;
                if (bit == 0) {
                    c0++;
                }
            }
        }
        double p0 = c0 / (double) n;
        double p1 = 1.0 - p0;
        double hBits = 0.0;
        if (p0 > 0) {
            hBits -= p0 * (Math.log(p0) / Math.log(2));
        }
        if (p1 > 0) {
            hBits -= p1 * (Math.log(p1) / Math.log(2));
        }
        return new BitEntropy(p0, p1, hBits);
    }

    private static long[] histogram(BufferedImage gray) {
        long[] h = new long[256];
        WritableRaster r = gray.getRaster();
        int w = gray.getWidth();
        int height = gray.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < w; x++) {
                int v = r.getSample(x, y, 0) & 0xFF;
                h[v]++;
            }
        }
        return h;
    }

    private static void saveHistogram(long[] hist, Path outPng, String title) throws IOException {
        int maxW = 512;
        int barAreaH = 220;
        int top = 40;
        BufferedImage img = new BufferedImage(maxW, top + barAreaH + 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawString(title, 8, 18);
        long max = 1;
        for (long v : hist) {
            if (v > max) {
                max = v;
            }
        }
        for (int i = 0; i < 256; i++) {
            int h = (int) Math.round((double) hist[i] / max * barAreaH);
            int x = i * maxW / 256;
            int bw = Math.max(1, (i + 1) * maxW / 256 - x);
            g.setColor(new Color(60, 60, 60));
            g.fillRect(x, top + barAreaH - h, bw, h);
        }
        g.dispose();
        ImageIO.write(img, "png", outPng.toFile());
    }

    private static void saveAbsDiffScaled(BufferedImage a, BufferedImage b, Path outPng) throws IOException {
        int w = a.getWidth();
        int h = a.getHeight();
        WritableRaster ra = a.getRaster();
        WritableRaster rb = b.getRaster();
        BufferedImage d = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster rd = d.getRaster();
        int maxd = 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dv = Math.abs((ra.getSample(x, y, 0) & 0xFF) - (rb.getSample(x, y, 0) & 0xFF));
                if (dv > maxd) {
                    maxd = dv;
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dv = Math.abs((ra.getSample(x, y, 0) & 0xFF) - (rb.getSample(x, y, 0) & 0xFF));
                int s = (int) Math.round(255.0 * dv / maxd);
                rd.setSample(x, y, 0, s);
            }
        }
        ImageIO.write(d, "png", outPng.toFile());
    }

    private static double mse(BufferedImage a, BufferedImage b) {
        int w = a.getWidth();
        int h = a.getHeight();
        WritableRaster ra = a.getRaster();
        WritableRaster rb = b.getRaster();
        double sum = 0.0;
        long n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int va = ra.getSample(x, y, 0) & 0xFF;
                int vb = rb.getSample(x, y, 0) & 0xFF;
                int d = va - vb;
                sum += (double) d * d;
                n++;
            }
        }
        return sum / n;
    }

    private static double psnr(double mse) {
        if (mse <= 0.0) {
            return 99.99;
        }
        return 10.0 * Math.log10((L * L) / mse);
    }

    /**
     * SSIM для полутона: окно 7×7, равномерное усреднение (валидная область без полей).
     */
    private static double ssimGray(BufferedImage a, BufferedImage b) {
        int w = a.getWidth();
        int h = a.getHeight();
        WritableRaster ra = a.getRaster();
        WritableRaster rb = b.getRaster();
        double sum = 0.0;
        long cnt = 0;
        for (int y = SSIM_R; y < h - SSIM_R; y++) {
            for (int x = SSIM_R; x < w - SSIM_R; x++) {
                double mux = 0, muy = 0, muxx = 0, muyy = 0, mxy = 0;
                int n = SSIM_WIN * SSIM_WIN;
                for (int dy = -SSIM_R; dy <= SSIM_R; dy++) {
                    for (int dx = -SSIM_R; dx <= SSIM_R; dx++) {
                        double xv = ra.getSample(x + dx, y + dy, 0) & 0xFF;
                        double yv = rb.getSample(x + dx, y + dy, 0) & 0xFF;
                        mux += xv;
                        muy += yv;
                        muxx += xv * xv;
                        muyy += yv * yv;
                        mxy += xv * yv;
                    }
                }
                mux /= n;
                muy /= n;
                double sx = muxx / n - mux * mux;
                double sy = muyy / n - muy * muy;
                double sxy = mxy / n - mux * muy;
                if (sx < 0) {
                    sx = 0;
                }
                if (sy < 0) {
                    sy = 0;
                }
                double num = (2 * mux * muy + C1) * (2 * sxy + C2);
                double den = (mux * mux + muy * muy + C1) * (sx + sy + C2);
                if (den > 0) {
                    sum += num / den;
                    cnt++;
                }
            }
        }
        return cnt == 0 ? Double.NaN : sum / cnt;
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String safeName(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }
}
