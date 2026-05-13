import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Цифровой водяной знак: LSB с порядком по секретному ключу; адаптивное внедрение (окно 3×3):
 * вариант 1 — локальный градиент (величина); вариант 2 — дисперсия; вариант 3 — локальный контраст (max−min).
 * Объём внедряемых битов не меньше половины ёмкости одной младшей плоскости (W×H бит).
 */
public final class WatermarkEngine {

    /** Три варианта адаптивного порядка внедрения из методички. */
    public enum AdaptiveVariant {
        /** По величине градиента яркости (центральные разности). */
        GRADIENT,
        /** По локальной дисперсии в окне 3×3. */
        VARIANCE,
        /** По размаху яркости max−min в окне 3×3. */
        CONTRAST
    }

    private WatermarkEngine() {
    }

    private static int sampleGray(WritableRaster r, int w, int h, int x, int y) {
        x = Math.min(w - 1, Math.max(0, x));
        y = Math.min(h - 1, Math.max(0, y));
        return r.getSample(x, y, 0) & 0xFF;
    }

    public static BufferedImage readImage(File f) throws IOException {
        BufferedImage img = ImageIO.read(f);
        if (img == null) {
            throw new IOException("Cannot read: " + f);
        }
        return toByteGray(img);
    }

    public static BufferedImage toByteGray(BufferedImage src) {
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
                yv = Math.max(0, Math.min(255, yv));
                dr.setSample(x, y, 0, yv);
            }
        }
        return dst;
    }

    public static BufferedImage copyGray(BufferedImage src) {
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

    public static long seedFromKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long s = 0;
            for (int i = 0; i < 8; i++) {
                s = (s << 8) | (d[i] & 0xFFL);
            }
            return s;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Перестановка индексов пикселей 0..wh-1 (строка: y*w+x). */
    public static int[] permutationFromKey(int w, int h, String key) {
        int n = w * h;
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = i;
        }
        Random rnd = new Random(seedFromKey(key));
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        return p;
    }

    public static int[] linearizeLogoBits(BufferedImage logoGrayBinary) {
        int lw = logoGrayBinary.getWidth();
        int lh = logoGrayBinary.getHeight();
        WritableRaster lr = logoGrayBinary.getRaster();
        int[] bits = new int[lw * lh];
        int k = 0;
        for (int y = 0; y < lh; y++) {
            for (int x = 0; x < lw; x++) {
                int v = lr.getSample(x, y, 0) & 0xFF;
                bits[k++] = v >= 128 ? 1 : 0;
            }
        }
        return bits;
    }

    /** Логотип в ч/б: порог по яркости. */
    public static BufferedImage binarizeLogo(BufferedImage logoGray) {
        int w = logoGray.getWidth();
        int h = logoGray.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster ir = logoGray.getRaster();
        WritableRaster or = out.getRaster();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = ir.getSample(x, y, 0) & 0xFF;
                or.setSample(x, y, 0, v >= 128 ? 255 : 0);
            }
        }
        return out;
    }

    public static BufferedImage scaleIfNeeded(BufferedImage src, int maxPixels) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w * h <= maxPixels) {
            return src;
        }
        double scale = Math.sqrt((double) maxPixels / (w * h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    /**
     * Число бит для внедрения: не меньше ceil(WH/2), не больше WH, не меньше длины логотипа (чтобы влез весь шаблон хотя бы раз).
     */
    public static int embedBitLength(int w, int h, int logoBits) {
        int cap = w * h;
        int half = (cap + 1) / 2;
        int need = Math.max(half, logoBits);
        return Math.min(cap, need);
    }

    public static int[] buildStream(int[] logoBits, int L) {
        int[] s = new int[L];
        if (logoBits.length == 0) {
            return s;
        }
        for (int i = 0; i < L; i++) {
            s[i] = logoBits[i % logoBits.length];
        }
        return s;
    }

    public static void setLsb(WritableRaster r, int x, int y, int bit) {
        int v = r.getSample(x, y, 0) & 0xFF;
        v = (v & 0xFE) | (bit & 1);
        r.setSample(x, y, 0, v);
    }

    public static int getLsb(WritableRaster r, int x, int y) {
        return r.getSample(x, y, 0) & 1;
    }

    public static int indexToX(int idx, int w) {
        return idx % w;
    }

    public static int indexToY(int idx, int w) {
        return idx / w;
    }

    /** Локальная дисперсия яркости в окне 3×3. */
    public static double localVariance3x3(BufferedImage gray, int cx, int cy) {
        WritableRaster r = gray.getRaster();
        int w = gray.getWidth();
        int h = gray.getHeight();
        double sum = 0;
        double sum2 = 0;
        int n = 9;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                double v = sampleGray(r, w, h, cx + dx, cy + dy);
                sum += v;
                sum2 += v * v;
            }
        }
        double mean = sum / n;
        return Math.max(0.0, sum2 / n - mean * mean);
    }

    /** Величина градиента по центральным разностям (аппроксимация производных). */
    public static double localGradientMagnitude(BufferedImage gray, int cx, int cy) {
        WritableRaster r = gray.getRaster();
        int w = gray.getWidth();
        int h = gray.getHeight();
        double gx = sampleGray(r, w, h, cx + 1, cy) - sampleGray(r, w, h, cx - 1, cy);
        double gy = sampleGray(r, w, h, cx, cy + 1) - sampleGray(r, w, h, cx, cy - 1);
        return Math.hypot(gx, gy);
    }

    /** Локальный контраст: размах яркости max−min в окне 3×3. */
    public static double localContrastRange3x3(BufferedImage gray, int cx, int cy) {
        WritableRaster r = gray.getRaster();
        int w = gray.getWidth();
        int h = gray.getHeight();
        int min = 255;
        int max = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int v = sampleGray(r, w, h, cx + dx, cy + dy);
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        return max - min;
    }

    public static double adaptiveLocalScore(BufferedImage gray, int cx, int cy, AdaptiveVariant variant) {
        switch (variant) {
            case GRADIENT:
                return localGradientMagnitude(gray, cx, cy);
            case VARIANCE:
                return localVariance3x3(gray, cx, cy);
            case CONTRAST:
                return localContrastRange3x3(gray, cx, cy);
            default:
                throw new IllegalArgumentException(String.valueOf(variant));
        }
    }

    /**
     * Порядок индексов пикселей по убыванию локального критерия (внедрение сначала в «сложных» по метрике местах).
     */
    public static int[] adaptiveEmbeddingOrder(BufferedImage gray, AdaptiveVariant variant) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        int n = w * h;
        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            int x = i % w;
            int y = i / w;
            score[i] = adaptiveLocalScore(gray, x, y, variant);
        }
        Integer[] ord = new Integer[n];
        for (int i = 0; i < n; i++) {
            ord[i] = i;
        }
        Arrays.sort(ord, (a, b) -> Double.compare(score[b], score[a]));
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = ord[i];
        }
        return order;
    }
    public static EmbedResult embedKeyLsb(BufferedImage cover, int[] logoBits, String key) {
        BufferedImage stego = copyGray(cover);
        int w = stego.getWidth();
        int h = stego.getHeight();
        int L = embedBitLength(w, h, logoBits.length);
        int[] stream = buildStream(logoBits, L);
        int[] perm = permutationFromKey(w, h, key);
        WritableRaster r = stego.getRaster();
        for (int i = 0; i < L; i++) {
            int idx = perm[i];
            int x = indexToX(idx, w);
            int y = indexToY(idx, w);
            setLsb(r, x, y, stream[i]);
        }
        return new EmbedResult(stego, L, perm, null);
    }

    public static EmbedResult embedAdaptive(BufferedImage cover, int[] logoBits, AdaptiveVariant variant) {
        BufferedImage stego = copyGray(cover);
        int w = stego.getWidth();
        int h = stego.getHeight();
        int L = embedBitLength(w, h, logoBits.length);
        int[] stream = buildStream(logoBits, L);
        int[] order = adaptiveEmbeddingOrder(cover, variant);
        WritableRaster r = stego.getRaster();
        for (int i = 0; i < L; i++) {
            int idx = order[i];
            int x = indexToX(idx, w);
            int y = indexToY(idx, w);
            setLsb(r, x, y, stream[i]);
        }
        return new EmbedResult(stego, L, null, order);
    }

    public static int[] extractKeyLsb(BufferedImage stego, String key, int L) {
        int w = stego.getWidth();
        int h = stego.getHeight();
        int[] perm = permutationFromKey(w, h, key);
        WritableRaster r = stego.getRaster();
        int[] bits = new int[L];
        for (int i = 0; i < L; i++) {
            int idx = perm[i];
            bits[i] = getLsb(r, indexToX(idx, w), indexToY(idx, w));
        }
        return bits;
    }

    public static int[] extractAdaptive(BufferedImage cover, BufferedImage stego, int L, AdaptiveVariant variant) {
        int w = stego.getWidth();
        int[] order = adaptiveEmbeddingOrder(cover, variant);
        WritableRaster r = stego.getRaster();
        int[] bits = new int[L];
        for (int i = 0; i < L; i++) {
            int idx = order[i];
            bits[i] = getLsb(r, indexToX(idx, w), indexToY(idx, w));
        }
        return bits;
    }

    public static BufferedImage bitsToLogoBitmap(int[] bits, int lw, int lh) {
        BufferedImage out = new BufferedImage(lw, lh, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster or = out.getRaster();
        int n = lw * lh;
        for (int i = 0; i < n; i++) {
            int v = i < bits.length && bits[i] == 1 ? 255 : 0;
            int x = i % lw;
            int y = i / lw;
            or.setSample(x, y, 0, v);
        }
        return out;
    }

    public static int bitErrors(int[] a, int[] b, int len) {
        int e = 0;
        for (int i = 0; i < len; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) {
                e++;
            }
        }
        return e;
    }

    public static double psnr(BufferedImage a, BufferedImage b) {
        int w = a.getWidth();
        int h = a.getHeight();
        WritableRaster ra = a.getRaster();
        WritableRaster rb = b.getRaster();
        double sum = 0;
        long n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int da = ra.getSample(x, y, 0) & 0xFF;
                int db = rb.getSample(x, y, 0) & 0xFF;
                int d = da - db;
                sum += (double) d * d;
                n++;
            }
        }
        double mse = sum / n;
        if (mse <= 0) {
            return 99.99;
        }
        return 10.0 * Math.log10((255.0 * 255.0) / mse);
    }

    public static final class EmbedResult {
        public final BufferedImage stego;
        public final int bitLength;
        public final int[] keyPermutation;
        /** Порядок пикселей при адаптивном внедрении (null для LSB по ключу). */
        public final int[] adaptiveOrder;

        EmbedResult(BufferedImage stego, int bitLength, int[] keyPermutation, int[] adaptiveOrder) {
            this.stego = stego;
            this.bitLength = bitLength;
            this.keyPermutation = keyPermutation;
            this.adaptiveOrder = adaptiveOrder;
        }
    }
}
