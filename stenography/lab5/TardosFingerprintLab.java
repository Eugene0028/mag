import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;
import javax.imageio.ImageIO;

/**
 * Лабораторная работа 5: генерация кода Тардоса, внедрение ЦОП в LSB,
 * моделирование коалиционной атаки и обнаружение участников коалиции.
 */
public final class TardosFingerprintLab {

    private static final int USERS = 10;
    private static final double EPSILON = 0.1;
    private static final String DEFAULT_COVER = "../container1/1.bmp";
    private static final String DEFAULT_KEY = "lab5-tardos-key";
    private static final String OUT_DIR = "out";

    private TardosFingerprintLab() {
    }

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8)) {
            System.out.println("Лабораторная 5 — цифровой отпечаток пальца на коде Тардоса");
            System.out.print("Путь к контейнеру BMP [../container1/1.bmp]: ");
            String coverPath = defaultIfBlank(sc.nextLine(), DEFAULT_COVER);
            System.out.print("Предполагаемый размер коалиции c: ");
            int c = readPositiveInt(sc.nextLine(), "c");
            System.out.print("Реальный размер коалиции c_real: ");
            int cReal = readPositiveInt(sc.nextLine(), "c_real");
            if (cReal > USERS) {
                throw new IOException("c_real не может быть больше " + USERS);
            }
            System.out.print("Список участников коалиции через запятую, 1..10 [первые c_real]: ");
            int[] coalition = parseCoalition(defaultIfBlank(sc.nextLine(), ""), cReal);
            System.out.print("Секретный ключ [" + DEFAULT_KEY + "]: ");
            String key = defaultIfBlank(sc.nextLine(), DEFAULT_KEY);

            runExperiment(Path.of(coverPath), c, cReal, coalition, key);
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                message = e.toString();
            }
            System.err.println("Ошибка: " + message);
        }
    }

    private static void runExperiment(Path coverPath, int c, int cReal, int[] coalition, String key) throws IOException {
        BufferedImage cover = readGray(coverPath.toFile());
        int capacity = cover.getWidth() * cover.getHeight();
        int m = tardosLength(c, USERS, EPSILON, capacity);
        TardosCode code = generateTardosCode(USERS, m, c, key);
        int[] positions = keyedPositions(cover.getWidth(), cover.getHeight(), key, m);

        Path out = Path.of(OUT_DIR);
        Path usersOut = out.resolve("users");
        Files.createDirectories(usersOut);

        for (int user = 0; user < USERS; user++) {
            BufferedImage fingerprinted = embedFingerprint(cover, positions, code.u[user]);
            ImageIO.write(fingerprinted, "bmp", usersOut.resolve(String.format(Locale.ROOT,
                    "user_%02d.bmp", user + 1)).toFile());
        }

        BufferedImage pirate = simulateInterleavingAttack(cover, positions, code.u, coalition, key);
        Path piratePath = out.resolve("pirate.bmp");
        ImageIO.write(pirate, "bmp", piratePath.toFile());

        int[] pirateWord = extractFingerprint(pirate, positions);
        double[] scores = accusationScores(code, pirateWord);
        Integer[] ranking = ranking(scores);
        boolean[] accusedTop = accuseTop(ranking, cReal);
        DetectionStats stats = detectionStats(coalition, accusedTop);

        writeProbabilities(out.resolve("probabilities.csv"), code.p);
        writeMatrix(out.resolve("tardos_matrix_U.csv"), code.u);
        writePirateWord(out.resolve("pirate_word.csv"), pirateWord);
        writeScores(out.resolve("scores.csv"), scores, coalition, accusedTop);
        writeSummary(out.resolve("summary.txt"), coverPath, c, cReal, coalition, key, capacity, m, piratePath, stats, ranking, scores);

        System.out.println("Готово. Результаты: " + out.toAbsolutePath());
        System.out.println("Длина кода m = " + m + ", емкость контейнера = " + capacity + " бит");
        System.out.println("Коалиция: " + coalitionToString(coalition));
        System.out.println("Обнаружены top-" + cReal + ": " + accusedToString(accusedTop));
        System.out.println("TP=" + stats.truePositive + ", FP=" + stats.falsePositive + ", FN=" + stats.falseNegative);
        System.out.println("Основные файлы: tardos_matrix_U.csv, users/user_XX.bmp, pirate.bmp, scores.csv, summary.txt");
    }

    private static int tardosLength(int c, int n, double epsilon, int capacity) {
        int theoretical = (int) Math.ceil(100.0 * c * c * Math.log(n / epsilon));
        return Math.min(capacity, Math.max(1, theoretical));
    }

    private static TardosCode generateTardosCode(int users, int m, int c, String key) {
        Random rnd = new Random(seedFromKey("tardos:" + key + ":" + c + ":" + m));
        double[] p = new double[m];
        int[][] u = new int[users][m];
        double cutoff = Math.min(0.25, 1.0 / (300.0 * Math.max(1, c)));
        double minAngle = Math.asin(Math.sqrt(cutoff));
        double maxAngle = Math.PI / 2.0 - minAngle;
        for (int j = 0; j < m; j++) {
            double angle = minAngle + rnd.nextDouble() * (maxAngle - minAngle);
            p[j] = Math.pow(Math.sin(angle), 2.0);
            for (int i = 0; i < users; i++) {
                u[i][j] = rnd.nextDouble() < p[j] ? 1 : 0;
            }
        }
        return new TardosCode(p, u);
    }

    private static BufferedImage embedFingerprint(BufferedImage cover, int[] positions, int[] fingerprint) {
        BufferedImage stego = copyGray(cover);
        WritableRaster r = stego.getRaster();
        int w = stego.getWidth();
        for (int j = 0; j < fingerprint.length; j++) {
            int idx = positions[j];
            int x = idx % w;
            int y = idx / w;
            int v = r.getSample(x, y, 0) & 0xFF;
            r.setSample(x, y, 0, (v & 0xFE) | fingerprint[j]);
        }
        return stego;
    }

    private static BufferedImage simulateInterleavingAttack(BufferedImage cover, int[] positions, int[][] u,
                                                           int[] coalition, String key) {
        BufferedImage pirate = copyGray(cover);
        WritableRaster r = pirate.getRaster();
        int w = pirate.getWidth();
        Random rnd = new Random(seedFromKey("attack:" + key + ":" + Arrays.toString(coalition)));
        for (int j = 0; j < positions.length; j++) {
            int selectedUser = coalition[rnd.nextInt(coalition.length)] - 1;
            int bit = u[selectedUser][j];
            int idx = positions[j];
            int x = idx % w;
            int y = idx / w;
            int v = r.getSample(x, y, 0) & 0xFF;
            r.setSample(x, y, 0, (v & 0xFE) | bit);
        }
        return pirate;
    }

    private static int[] extractFingerprint(BufferedImage image, int[] positions) {
        int[] word = new int[positions.length];
        WritableRaster r = image.getRaster();
        int w = image.getWidth();
        for (int j = 0; j < positions.length; j++) {
            int idx = positions[j];
            word[j] = r.getSample(idx % w, idx / w, 0) & 1;
        }
        return word;
    }

    private static double[] accusationScores(TardosCode code, int[] pirateWord) {
        double[] scores = new double[code.u.length];
        for (int i = 0; i < code.u.length; i++) {
            double s = 0.0;
            for (int j = 0; j < pirateWord.length; j++) {
                int x = code.u[i][j];
                int y = pirateWord[j];
                double p = code.p[j];
                if (y == 1) {
                    s += x == 1 ? Math.sqrt((1.0 - p) / p) : -Math.sqrt(p / (1.0 - p));
                } else {
                    s += x == 0 ? Math.sqrt(p / (1.0 - p)) : -Math.sqrt((1.0 - p) / p);
                }
            }
            scores[i] = s;
        }
        return scores;
    }

    private static Integer[] ranking(double[] scores) {
        Integer[] order = new Integer[scores.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> scores[i]).reversed());
        return order;
    }

    private static boolean[] accuseTop(Integer[] ranking, int cReal) {
        boolean[] accused = new boolean[ranking.length];
        for (int i = 0; i < cReal && i < ranking.length; i++) {
            accused[ranking[i]] = true;
        }
        return accused;
    }

    private static DetectionStats detectionStats(int[] coalition, boolean[] accused) {
        boolean[] colluder = new boolean[accused.length];
        for (int user : coalition) {
            colluder[user - 1] = true;
        }
        int tp = 0;
        int fp = 0;
        int fn = 0;
        for (int i = 0; i < accused.length; i++) {
            if (accused[i] && colluder[i]) {
                tp++;
            } else if (accused[i]) {
                fp++;
            } else if (colluder[i]) {
                fn++;
            }
        }
        return new DetectionStats(tp, fp, fn);
    }

    private static int[] keyedPositions(int w, int h, String key, int m) {
        int n = w * h;
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = i;
        }
        Random rnd = new Random(seedFromKey("positions:" + key));
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        return Arrays.copyOf(p, m);
    }

    private static BufferedImage readGray(File file) throws IOException {
        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            throw new IOException("Не удалось прочитать изображение: " + file);
        }
        return toByteGray(img);
    }

    private static BufferedImage toByteGray(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return copyGray(src);
        }
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster dr = dst.getRaster();
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int rgb = src.getRGB(x, y);
                int rr = (rgb >> 16) & 0xFF;
                int gg = (rgb >> 8) & 0xFF;
                int bb = rgb & 0xFF;
                int gray = (int) Math.round(0.299 * rr + 0.587 * gg + 0.114 * bb);
                dr.setSample(x, y, 0, Math.max(0, Math.min(255, gray)));
            }
        }
        return dst;
    }

    private static BufferedImage copyGray(BufferedImage src) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster sr = src.getRaster();
        WritableRaster dr = dst.getRaster();
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                dr.setSample(x, y, 0, sr.getSample(x, y, 0));
            }
        }
        return dst;
    }

    private static long seedFromKey(String key) {
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

    private static void writeProbabilities(Path path, double[] p) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("j;p_j");
            out.newLine();
            for (int j = 0; j < p.length; j++) {
                out.write(String.format(Locale.ROOT, "%d;%.10f", j + 1, p[j]));
                out.newLine();
            }
        }
    }

    private static void writeMatrix(Path path, int[][] u) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("user");
            for (int j = 0; j < u[0].length; j++) {
                out.write(";u" + (j + 1));
            }
            out.newLine();
            for (int i = 0; i < u.length; i++) {
                out.write(Integer.toString(i + 1));
                for (int bit : u[i]) {
                    out.write(";" + bit);
                }
                out.newLine();
            }
        }
    }

    private static void writePirateWord(Path path, int[] word) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("j;y_j");
            out.newLine();
            for (int j = 0; j < word.length; j++) {
                out.write((j + 1) + ";" + word[j]);
                out.newLine();
            }
        }
    }

    private static void writeScores(Path path, double[] scores, int[] coalition, boolean[] accused) throws IOException {
        boolean[] colluder = new boolean[scores.length];
        for (int user : coalition) {
            colluder[user - 1] = true;
        }
        Integer[] order = ranking(scores);
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("rank;user;score;real_colluder;accused_top_c_real");
            out.newLine();
            for (int rank = 0; rank < order.length; rank++) {
                int i = order[rank];
                out.write(String.format(Locale.ROOT, "%d;%d;%.6f;%s;%s",
                        rank + 1, i + 1, scores[i], colluder[i], accused[i]));
                out.newLine();
            }
        }
    }

    private static void writeSummary(Path path, Path coverPath, int c, int cReal, int[] coalition, String key,
                                     int capacity, int m, Path piratePath, DetectionStats stats,
                                     Integer[] ranking, double[] scores) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("Лабораторная 5 — код Тардоса и цифровой отпечаток пальца");
            out.newLine();
            out.write("Контейнер: " + coverPath);
            out.newLine();
            out.write("n = " + USERS + ", epsilon = " + EPSILON + ", c = " + c + ", c_real = " + cReal);
            out.newLine();
            out.write("Длина кода m = " + m + ", емкость контейнера = " + capacity + " бит");
            out.newLine();
            out.write("Ключ: " + key);
            out.newLine();
            out.write("Коалиция: " + coalitionToString(coalition));
            out.newLine();
            out.write("Пиратская версия: " + piratePath);
            out.newLine();
            out.write("Обнаружены top-" + cReal + ": " + accusedToString(accuseTop(ranking, cReal)));
            out.newLine();
            out.write("TP = " + stats.truePositive + ", FP = " + stats.falsePositive + ", FN = " + stats.falseNegative);
            out.newLine();
            out.write("Рейтинг пользователей по score:");
            out.newLine();
            for (int rank = 0; rank < ranking.length; rank++) {
                int user = ranking[rank] + 1;
                out.write(String.format(Locale.ROOT, "%d) user %d: %.6f", rank + 1, user, scores[user - 1]));
                out.newLine();
            }
        }
    }

    private static String defaultIfBlank(String value, String def) {
        String v = value == null ? "" : value.trim();
        return v.isEmpty() ? def : v;
    }

    private static int readPositiveInt(String raw, String name) throws IOException {
        try {
            int v = Integer.parseInt(raw.trim());
            if (v <= 0) {
                throw new IOException(name + " должен быть положительным.");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IOException(name + " должен быть целым числом.");
        }
    }

    private static int[] parseCoalition(String raw, int cReal) throws IOException {
        if (raw == null || raw.trim().isEmpty()) {
            int[] coalition = new int[cReal];
            for (int i = 0; i < cReal; i++) {
                coalition[i] = i + 1;
            }
            return coalition;
        }
        String[] parts = raw.split(",");
        if (parts.length != cReal) {
            throw new IOException("Нужно указать ровно " + cReal + " участников.");
        }
        List<Integer> users = new ArrayList<>();
        for (String part : parts) {
            int user = readPositiveInt(part, "номер пользователя");
            if (user > USERS) {
                throw new IOException("Номер пользователя должен быть в диапазоне 1.." + USERS);
            }
            if (users.contains(user)) {
                throw new IOException("Участники коалиции не должны повторяться.");
            }
            users.add(user);
        }
        int[] coalition = new int[cReal];
        for (int i = 0; i < cReal; i++) {
            coalition[i] = users.get(i);
        }
        return coalition;
    }

    private static String coalitionToString(int[] coalition) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < coalition.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(coalition[i]);
        }
        return sb.toString();
    }

    private static String accusedToString(boolean[] accused) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < accused.length; i++) {
            if (accused[i]) {
                if (!first) {
                    sb.append(',');
                }
                sb.append(i + 1);
                first = false;
            }
        }
        return sb.toString();
    }

    private static final class TardosCode {
        final double[] p;
        final int[][] u;

        TardosCode(double[] p, int[][] u) {
            this.p = p;
            this.u = u;
        }
    }

    private static final class DetectionStats {
        final int truePositive;
        final int falsePositive;
        final int falseNegative;

        DetectionStats(int truePositive, int falsePositive, int falseNegative) {
            this.truePositive = truePositive;
            this.falsePositive = falsePositive;
            this.falseNegative = falseNegative;
        }
    }
}
