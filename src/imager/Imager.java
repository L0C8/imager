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
            BufferedImage src = Dithering.loadImage(path);
            BufferedImage out = null;
            String methodName = "out";
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
                Dithering.saveImage(out, path, methodName);
            }

        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            e.printStackTrace();
        }

        in.close();
    }
    
}
