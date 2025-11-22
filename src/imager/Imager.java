package imager;

import imager.Editor.Dithering;

import java.awt.image.BufferedImage;
import java.io.IOException;
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
            boolean isGif = path.toLowerCase().endsWith(".gif");

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
