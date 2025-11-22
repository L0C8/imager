package imager;

import imager.Editor.Dithering;
import imager.Editor.FFmpegConverter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.File;
import java.util.Scanner;

public class Imager {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.println("Simple Imager Dithering Menu");
        System.out.println("1) Threshold");
        System.out.println("2) Random dither");
        System.out.println("3) Ordered Bayer");
        System.out.println("4) Ordered avoid-cluster");
        System.out.println("5) Floyd-Steinberg (error diffusion)");
        System.out.print("Choose method (1-5): ");
        int choice = Integer.parseInt(in.nextLine().trim());
        System.out.print("Enter input image path: ");
        String path = in.nextLine().trim();

        try {
            BufferedImage out = null;
            String methodName = "out";

            System.out.print("Enter scale (decimal multiplier e.g. 0.5, 1.0, 2.0, 5, default 1): ");
            String scaleInput = in.nextLine().trim();
            double scale = 1.0;
            if (!scaleInput.isEmpty()) {
                try {
                    scale = parseScale(scaleInput);
                } catch (Exception ex) {
                    System.out.println("Invalid scale, using 1.0");
                    scale = 1.0;
                }
            }
            if (scale <= 0) scale = 1.0;
            String lower = path.toLowerCase();
            boolean isGif = lower.endsWith(".gif");
            boolean isVideo = lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".mkv") || lower.endsWith(".avi");

            // If input is GIF, process frames and write an animated GIF
            if (isGif) {
                // For threshold method, ask threshold before processing
                int thr = 128;
                if (choice == 1) {
                    System.out.print("Enter threshold 0-255 (default 128): ");
                    String t = in.nextLine().trim();
                    if (!t.isEmpty()) {
                        try { thr = Integer.parseInt(t); } catch (NumberFormatException ex) { thr = 128; }
                    }
                }
                // Build an output tag
                String outTag;
                switch (choice) {
                    case 1: outTag = "threshold" + thr; break;
                    case 2: outTag = "randomAnim"; break;
                    case 3: outTag = "orderedBayerAnim"; break;
                    case 4: outTag = "orderedAvoidClusterAnim"; break;
                    case 5: outTag = "floydSteinbergAnim"; break;
                    default: outTag = "anim"; break;
                }
                if (scale != 1.0) outTag = outTag + "_resized_" + ((int) Math.round(scale * 100)) + "pct";

                // For threshold method, animatedDither currently uses a fixed threshold pattern.
                // We'll call animatedDither which applies per-frame processing. For threshold, we
                // don't currently pass a custom threshold per frame, so implementation will vary.
                Dithering.animatedDither(path, choice, scale, outTag);
                System.out.println("Animated GIF processing complete.");
                in.close();
                return;
            }

            if (isVideo) {
                System.out.println("Detected video input. Will apply per-frame dithering (requires ffmpeg).");
                System.out.print("Include audio in output? (y/n, default n): ");
                String inc = in.nextLine().trim().toLowerCase();
                boolean includeAudio = inc.equals("y") || inc.equals("yes");
                String audioCodec = "aac";
                int audioKbps = 16;
                if (includeAudio) {
                    System.out.print("Audio codec (aac/libopus/libmp3lame), default aac: ");
                    String ac = in.nextLine().trim();
                    if (!ac.isEmpty()) audioCodec = ac;
                    System.out.print("Audio bitrate kbps (e.g. 12,16,24), default 16: ");
                    String kb = in.nextLine().trim();
                    if (!kb.isEmpty()) {
                        try { audioKbps = Integer.parseInt(kb); } catch (Exception ex) { audioKbps = 16; }
                    }
                }

                File inFile = new File(path);
                String name = inFile.getName();
                int dot = name.lastIndexOf('.');
                String base = (dot >= 0) ? name.substring(0, dot) : name;
                String outName = base + "_dithered.mp4";
                String outPath = new File(inFile.getParentFile(), outName).getAbsolutePath();
                System.out.println("Processing video (this may take a while)...");
                try {
                    if (!FFmpegConverter.isFfmpegAvailable()) {
                        System.err.println("ffmpeg (and ffprobe) not found on PATH. Install ffmpeg and try again.");
                        System.err.println("See documentation or run: sudo apt install ffmpeg  (on Debian/Ubuntu)");
                    } else {
                        boolean ok = FFmpegConverter.ditherVideo(path, outPath, choice, scale, includeAudio, audioCodec, audioKbps);
                        if (ok) System.out.println("Saved dithered video: " + outPath);
                        else System.err.println("Video dithering failed. Ensure ffmpeg is installed and available on PATH.");
                    }
                    
                } catch (Exception ex) {
                    System.err.println("Error during video dithering: " + ex.getMessage());
                    ex.printStackTrace();
                }
                in.close();
                return;
            }

            BufferedImage src = Dithering.loadImage(path);
            if (scale != 1.0) {
                src = Dithering.resize(src, scale);
            }
            switch (choice) {
                case 1:
                    System.out.print("Enter threshold 0-255 (default 128): ");
                    String t = in.nextLine().trim();
                    int thr = 128;
                    if (!t.isEmpty()) {
                        try { thr = Integer.parseInt(t); } catch (NumberFormatException ex) { thr = 128; }
                    }
                    out = Dithering.threshold(src, thr);
                    methodName = "threshold" + thr;
                    break;
                case 2:
                    out = Dithering.randomDither(src);
                    methodName = "random";
                    break;
                case 3:
                    out = Dithering.orderedBayer(src);
                    methodName = "orderedBayer";
                    break;
                case 4:
                    out = Dithering.orderedAvoidCluster(src);
                    methodName = "orderedAvoidCluster";
                    break;
                case 5:
                    out = Dithering.floydSteinberg(src);
                    methodName = "floydSteinberg";
                    break;
                default:
                    System.out.println("Invalid choice");
                    System.exit(1);
            }

            if (out != null) {
                if (scale != 1.0) {
                    methodName = methodName + "_resized_" + ((int) Math.round(scale * 100)) + "pct";
                }
                Dithering.saveImage(out, path, methodName);
            }

        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            e.printStackTrace();
        }

        in.close();
    }

    private static double parseScale(String s) {
        if (s == null || s.isEmpty()) return 1.0;
        s = s.trim();
        return Double.parseDouble(s);
    }
    
}
