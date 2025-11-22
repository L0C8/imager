package imager.Editor;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.w3c.dom.Node;

public class Dithering {

    public static BufferedImage loadImage(String path) throws IOException {
        return ImageIO.read(new File(path));
    }

    public static void saveImage(BufferedImage img, String inputPath, String methodName) throws IOException {
        File in = new File(inputPath);
        String name = in.getName();
        int dot = name.lastIndexOf('.');
        String base = (dot >= 0) ? name.substring(0, dot) : name;
        String outPath = new File(in.getParentFile(), base + "_" + methodName + ".png").getAbsolutePath();
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
        return randomDitherPerFrame(src, 0); // seed 0 for static images
    }

    private static BufferedImage randomDitherPerFrame(BufferedImage src, long seed) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(seed);
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
                int jitter = rnd.nextInt(33) - 16;
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

    public static List<BufferedImage> loadGifFrames(String path, List<Integer> delaysCs) throws IOException {
        List<BufferedImage> frames = new ArrayList<>();
        ImageInputStream stream = ImageIO.createImageInputStream(new File(path));
        Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw new IOException("No image reader found");
        ImageReader reader = readers.next();
        reader.setInput(stream);

        int num = reader.getNumImages(true);
        for (int i = 0; i < num; i++) {
            BufferedImage frame = reader.read(i);
            frames.add(frame);
            try {
                IIOMetadata meta = reader.getImageMetadata(i);
                int delay = extractGifDelay(meta);
                delaysCs.add(delay);
            } catch (Exception e) {
                delaysCs.add(10);
            }
        }
        reader.dispose();
        stream.close();
        return frames;
    }

    private static int extractGifDelay(IIOMetadata meta) {
        if (meta == null) return 10;
        String[] names = meta.getMetadataFormatNames();
        for (String name : names) {
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(name);
            Node node = root.getFirstChild();
            while (node != null) {
                if ("GraphicControlExtension".equals(node.getNodeName())) {
                    IIOMetadataNode gce = (IIOMetadataNode) node;
                    String delayTime = gce.getAttribute("delayTime");
                    try {
                        return Integer.parseInt(delayTime);
                    } catch (Exception e) {
                        return 10;
                    }
                }
                node = node.getNextSibling();
            }
        }
        return 10;
    }

    public static void writeAnimatedGif(List<BufferedImage> frames, String outPath,
                                        int[] delaysCs, int loopCount,
                                        int canvasWidth, int canvasHeight) throws IOException {
        if (frames.isEmpty()) throw new IllegalArgumentException("No frames");

        ImageWriter writer = ImageIO.getImageWritersBySuffix("gif").next();
        ImageOutputStream output = ImageIO.createImageOutputStream(new File(outPath));
        writer.setOutput(output);

        ImageWriteParam params = writer.getDefaultWriteParam();

            IIOMetadata streamMeta = writer.getDefaultStreamMetadata(params);
            if (streamMeta != null) {
                String streamFormat = streamMeta.getNativeMetadataFormatName();
                IIOMetadataNode streamRoot = new IIOMetadataNode(streamFormat);
                IIOMetadataNode appExtensions = new IIOMetadataNode("ApplicationExtensions");
                IIOMetadataNode appNode = new IIOMetadataNode("ApplicationExtension");
                appNode.setAttribute("applicationID", "NETSCAPE");
                appNode.setAttribute("authenticationCode", "2.0");
                int loop = (loopCount < 0) ? 0 : loopCount;
                byte[] loopBytesStream = new byte[]{1, (byte) (loop & 0xFF), (byte) ((loop >> 8) & 0xFF)};
                appNode.setUserObject(loopBytesStream);
                appExtensions.appendChild(appNode);
                streamRoot.appendChild(appExtensions);
                try {
                    streamMeta.mergeTree(streamFormat, streamRoot);
                } catch (Exception ex) {
                    // ignore
                }
            }

            // start sequence with stream metadata
            writer.prepareWriteSequence(streamMeta);

        for (int i = 0; i < frames.size(); i++) {
            BufferedImage img = frames.get(i);

            IIOMetadata frameMeta = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(img), params);

            IIOMetadataNode gce = new IIOMetadataNode("GraphicControlExtension");
            gce.setAttribute("disposalMethod", "none"); // or "restoreToBackground"
            gce.setAttribute("userInputFlag", "FALSE");
            gce.setAttribute("transparentColorFlag", "FALSE");
            int delay = (delaysCs != null && i < delaysCs.length) ? delaysCs[i] : 10;
            gce.setAttribute("delayTime", String.valueOf(delay));
            gce.setAttribute("transparentColorIndex", "0");

            IIOMetadataNode frameRoot = new IIOMetadataNode(frameMeta.getNativeMetadataFormatName());
            frameRoot.appendChild(gce);

            // Image descriptor (position + size)
            IIOMetadataNode imgDesc = new IIOMetadataNode("ImageDescriptor");
            imgDesc.setAttribute("imageLeftPosition", "0");
            imgDesc.setAttribute("imageTopPosition", "0");
            imgDesc.setAttribute("imageWidth", String.valueOf(img.getWidth()));
            imgDesc.setAttribute("imageHeight", String.valueOf(img.getHeight()));
            frameRoot.appendChild(imgDesc);

            try {
                frameMeta.mergeTree(frameMeta.getNativeMetadataFormatName(), frameRoot);
            } catch (IIOInvalidTreeException e) {
                // ignore
            }

            IIOImage iioImage = new IIOImage(img, null, frameMeta);
            writer.writeToSequence(iioImage, params);
        }

        writer.endWriteSequence();
        output.close();
        writer.dispose();
        System.out.println("Saved animated GIF: " + outPath);
    }

    public static void animatedDither(String inputPath, int methodChoice, double scale, String outMethodTag) throws IOException {
        List<Integer> delays = new ArrayList<>();
        List<BufferedImage> frames = loadGifFrames(inputPath, delays);
        if (frames.size() > 1) {
            frames.remove(0);
            if (!delays.isEmpty()) delays.remove(0);
        }

        int originalWidth = frames.get(0).getWidth();
        int originalHeight = frames.get(0).getHeight();
        int canvasW = (int) Math.round(originalWidth * scale);
        int canvasH = (int) Math.round(originalHeight * scale);

        List<BufferedImage> processedFrames = new ArrayList<>();

        for (int i = 0; i < frames.size(); i++) {
            BufferedImage frame = frames.get(i);
            if (scale != 1.0) {
                frame = resize(frame, scale);
            }

            BufferedImage dithered;
            switch (methodChoice) {
                case 1 -> dithered = threshold(frame, 128);
                case 2 -> dithered = randomDitherPerFrame(frame, i * 7919L);
                case 3 -> dithered = orderedBayer(frame);
                case 4 -> dithered = orderedAvoidCluster(frame);
                case 5 -> dithered = floydSteinberg(frame);
                default -> dithered = floydSteinberg(frame);
            }
            processedFrames.add(dithered);
        }

        int[] delayArray = delays.stream().mapToInt(Integer::intValue).toArray();

        File inFile = new File(inputPath);
        String name = inFile.getName();
        int dot = name.lastIndexOf('.');
        String base = (dot >= 0) ? name.substring(0, dot) : name;
        String suffix = scale != 1.0 ? "_" + outMethodTag + "_x" + String.format("%.2f", scale) : "_" + outMethodTag;
        String outPath = new File(inFile.getParentFile(), base + suffix + ".gif").getAbsolutePath();

        writeAnimatedGif(processedFrames, outPath, delayArray, 0, canvasW, canvasH);
    }
}
