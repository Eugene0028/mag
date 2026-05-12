import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Scanner;
import javax.imageio.ImageIO;

public class StegoTool {

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            System.out.println("Select mode:");
            System.out.println("1 - Extract bit plane");
            System.out.println("2 - Embed message");
            System.out.println("3 - Extract message");
            System.out.print("Mode: ");
            int mode = sc.nextInt();
            sc.nextLine();

            System.out.print("Path to BMP (e.g., container1bmp/t1.bmp): ");
            String imgPath = sc.nextLine();

            System.out.print("Bit number k (1-8, 1 is LSB): ");
            int k = sc.nextInt();
            sc.nextLine();

            switch (mode) {
                case 1:
                    getBitPlane(imgPath, k);
                    break;
                case 2:
                    System.out.print("Path to secret text file: ");
                    String txtPath = sc.nextLine();
                    embedData(imgPath, txtPath, k);
                    break;
                case 3:
                    System.out.print("Number of bits to extract (e.g., 245760): ");
                    int bitLen = sc.nextInt();
                    extractData(imgPath, k, bitLen);
                    break;
                default:
                    System.out.println("Invalid mode.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static BufferedImage readImageOrThrow(String path) throws IOException {
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) {
            throw new IOException("Cannot read image: " + path);
        }
        return img;
    }

    private static void checkK(int k) {
        if (k < 1 || k > 8) {
            throw new IllegalArgumentException("k must be in 1..8");
        }
    }

    // 1) Извлечение битовой плоскости
    public static void getBitPlane(String path, int k) throws IOException {
        checkK(k);
        BufferedImage img = readImageOrThrow(path);
        int w = img.getWidth();
        int h = img.getHeight();
        int shift = k - 1;

        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster src = img.getRaster();
        WritableRaster dst = res.getRaster();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = src.getSample(x, y, 0) & 0xFF;
                int bit = (v >> shift) & 1;
                dst.setSample(x, y, 0, bit == 1 ? 255 : 0);
            }
        }
        ImageIO.write(res, "bmp", new File("plane_" + k + ".bmp"));
        System.out.println("Success: plane_" + k + ".bmp");
    }

    // 2) Внедрение данных
    public static void embedData(String imgPath, String txtPath, int k) throws IOException {
        checkK(k);
        BufferedImage img = readImageOrThrow(imgPath);
        byte[] message = Files.readAllBytes(new File(txtPath).toPath());

        int w = img.getWidth();
        int h = img.getHeight();
        int capacity = w * h;
        WritableRaster r = img.getRaster();
        int shift = k - 1;
        int pixelIdx = 0;
        int totalBits = 0;

        outer:
        for (byte b : message) {
            for (int i = 7; i >= 0; i--) {
                if (pixelIdx >= capacity) {
                    System.err.println("Warning: container full, message truncated.");
                    break outer;
                }
                int x = pixelIdx % w;
                int y = pixelIdx / w;
                int v = r.getSample(x, y, 0) & 0xFF;
                int bit = (b >> i) & 1;
                v = (v & ~(1 << shift)) | (bit << shift);
                r.setSample(x, y, 0, v);
                pixelIdx++;
                totalBits++;
            }
        }
        ImageIO.write(img, "bmp", new File("stego_result.bmp"));
        System.out.println("Success write " + totalBits + " bits in stego_result.bmp");
    }

    // 3) Извлечение сообщения
    public static void extractData(String path, int k, int bitLen) throws IOException {
        checkK(k);
        BufferedImage img = readImageOrThrow(path);
        int w = img.getWidth();
        int h = img.getHeight();
        if (bitLen < 0) {
            throw new IllegalArgumentException("bitLen must be non-negative");
        }
        if (bitLen > (long) w * h) {
            throw new IOException("bitLen exceeds image capacity (" + ((long) w * h) + " bits)");
        }

        WritableRaster r = img.getRaster();
        byte[] result = new byte[(bitLen + 7) / 8];

        int shift = k - 1;
        for (int i = 0; i < bitLen; i++) {
            int x = i % w;
            int y = i / w;
            int v = r.getSample(x, y, 0) & 0xFF;
            int bit = (v >> shift) & 1;
            result[i / 8] |= (bit << (7 - (i % 8)));
        }
        Files.write(new File("extracted.txt").toPath(), result);
        System.out.println("Message saved in extracted.txt");
    }
}
