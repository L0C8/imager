package imager.Editor;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

public class Dithering {

	public static BufferedImage loadImage(String path) throws IOException {
		return ImageIO.read(new File(path));
	}

	public static void saveImage(BufferedImage img, String inputPath, String methodName) throws IOException {
		String outPath;
		File in = new File(inputPath);
		String name = in.getName();
		int dot = name.lastIndexOf('.');
		String base = (dot >= 0) ? name.substring(0, dot) : name;
		outPath = new File(in.getParentFile(), base + "_" + methodName + ".png").getAbsolutePath();
		ImageIO.write(img, "PNG", new File(outPath));
		System.out.println("Saved: " + outPath);
	}

	private static int luminance(int rgb) {
		Color c = new Color(rgb);
		return (int) (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue());
	}

	public static BufferedImage threshold(BufferedImage src, int threshold) {
		int w = src.getWidth(), h = src.getHeight();
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int lum = luminance(src.getRGB(x, y));
				int val = (lum >= threshold) ? 0xFFFFFF : 0x000000;
				dst.setRGB(x, y, val);
			}
		}
		return dst;
	}

	public static BufferedImage randomDither(BufferedImage src) {
		int w = src.getWidth(), h = src.getHeight();
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Random rnd = new Random();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int lum = luminance(src.getRGB(x, y));
				int r = rnd.nextInt(256);
				int val = (lum >= r) ? 0xFFFFFF : 0x000000;
				dst.setRGB(x, y, val);
			}
		}
		return dst;
	}

	public static BufferedImage orderedBayer(BufferedImage src) {
		int[][] bayer4 = {
				{0, 8, 2, 10},
				{12, 4, 14, 6},
				{3, 11, 1, 9},
				{15, 7, 13, 5}
		};
		int n = 4;
		int w = src.getWidth(), h = src.getHeight();
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int lum = luminance(src.getRGB(x, y));
				int i = x % n, j = y % n;
				int threshold = (int) ((bayer4[j][i] + 0.5) * (255.0 / (n * n)));
				int val = (lum >= threshold) ? 0xFFFFFF : 0x000000;
				dst.setRGB(x, y, val);
			}
		}
		return dst;
	}

	public static BufferedImage orderedAvoidCluster(BufferedImage src) {
		// Use Bayer matrix but add a small randomized jitter to avoid regular clusters
		int[][] bayer4 = {
				{0, 8, 2, 10},
				{12, 4, 14, 6},
				{3, 11, 1, 9},
				{15, 7, 13, 5}
		};
		int n = 4;
		Random rnd = new Random(0xC0FFEE);
		int w = src.getWidth(), h = src.getHeight();
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int lum = luminance(src.getRGB(x, y));
				int i = x % n, j = y % n;
				int base = bayer4[j][i];
				int jitter = rnd.nextInt(33) - 16; // -16..+16
				int threshold = (int) ((base + 0.5) * (255.0 / (n * n))) + jitter;
				threshold = Math.max(0, Math.min(255, threshold));
				int val = (lum >= threshold) ? 0xFFFFFF : 0x000000;
				dst.setRGB(x, y, val);
			}
		}
		return dst;
	}

	public static BufferedImage floydSteinberg(BufferedImage src) {
		int w = src.getWidth(), h = src.getHeight();
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		float[][] gray = new float[h][w];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				gray[y][x] = luminance(src.getRGB(x, y));
			}
		}

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float old = gray[y][x];
				int newv = (old >= 128) ? 255 : 0;
				float err = old - newv;
				dst.setRGB(x, y, (newv == 255) ? 0xFFFFFF : 0x000000);
				if (x + 1 < w) gray[y][x + 1] += err * 7 / 16f;
				if (x - 1 >= 0 && y + 1 < h) gray[y + 1][x - 1] += err * 3 / 16f;
				if (y + 1 < h) gray[y + 1][x] += err * 5 / 16f;
				if (x + 1 < w && y + 1 < h) gray[y + 1][x + 1] += err * 1 / 16f;
			}
		}

		return dst;
	}

	public static BufferedImage resize(BufferedImage src, double scale) {
		if (scale <= 0) throw new IllegalArgumentException("scale must be > 0");
		int w = (int) Math.max(1, Math.round(src.getWidth() * scale));
		int h = (int) Math.max(1, Math.round(src.getHeight() * scale));
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		java.awt.Image tmp = src.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);
		java.awt.Graphics2D g2 = dst.createGraphics();
		g2.drawImage(tmp, 0, 0, null);
		g2.dispose();
		return dst;
	}

}
