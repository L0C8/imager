package imager.Editor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FFmpegConverter {

    public static boolean isFfmpegAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            int rc = finished ? p.exitValue() : -1;
            p.destroy();
            return rc == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean convertMp4ToGif(String inputMp4, String outputGif, int fps, int width) throws IOException, InterruptedException {
        // Use -2 for the automatic dimension to ensure the other dimension is divisible by 2 (required by many encoders)
        String scale = (width > 0) ? width + ":-2:flags=lanczos" : "-2:flags=lanczos";

        // palette file in working directory (deleted after)
        File palette = new File("palette.png");

        List<String> palCmd = new ArrayList<>(Arrays.asList(
                "ffmpeg", "-y", "-i", inputMp4,
                "-vf", "fps=" + fps + ",scale=" + scale + ",palettegen",
                "palette.png"
        ));

        ProcessResult r1 = execute(palCmd);
        if (r1.exitCode != 0) {
            if (palette.exists()) palette.delete();
            System.err.println("ffmpeg palettegen failed (exit " + r1.exitCode + "). Output:\n" + r1.output);
            return false;
        }

        List<String> gifCmd = new ArrayList<>(Arrays.asList(
                "ffmpeg", "-y", "-i", inputMp4, "-i", "palette.png",
                "-lavfi", "fps=" + fps + ",scale=" + scale + "[x];[x][1:v]paletteuse=dither=bayer",
                outputGif
        ));

        ProcessResult r2 = execute(gifCmd);

        if (palette.exists()) palette.delete();
        if (r2.exitCode != 0) {
            System.err.println("ffmpeg gif generation failed (exit " + r2.exitCode + "). Output:\n" + r2.output);
        }
        return r2.exitCode == 0;
    }

    public static boolean compressAudio(String inputFile, String outputFile, int bitrateKbps, String codec) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(inputFile);
        cmd.add("-vn"); // no video
        cmd.add("-c:a");
        cmd.add(codec);
        cmd.add("-b:a");
        cmd.add(bitrateKbps + "k");
        // force mono + lower sample rate for smaller size
        cmd.add("-ac"); cmd.add("1");
        cmd.add("-ar"); cmd.add("22050");
        cmd.add(outputFile);
        ProcessResult r = execute(cmd);
        if (r.exitCode != 0) {
            System.err.println("ffmpeg audio compression failed (exit " + r.exitCode + "). Output:\n" + r.output);
        }
        return r.exitCode == 0;
    }

    private static void consumeStream(final InputStream is) {
        new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = r.readLine()) != null) {
                    // intentionally no-op: keep ffmpeg output from blocking
                }
            } catch (IOException e) {
                // ignore
            }
        }).start();
    }

    private static class ProcessResult {
        int exitCode;
        String output;

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static ProcessResult execute(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (InputStream is = p.getInputStream();
             java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
            }
        } catch (IOException e) {
            // ignore
        }
        int rc = p.waitFor();
        return new ProcessResult(rc, out.toString());
    }

    /**
     * Compatibility shim. Some older builds called `ditherVideo(...)`.
     * This method provides a reasonable implementation:
     * - If `outputPath` ends with `.gif` (or is empty), it delegates to `convertMp4ToGif`.
     * - Otherwise it re-encodes the video using ffmpeg applying fps/scale and optionally keeping/compressing audio.
     *
     * Signature is intentionally general to match common expectations from older callers.
     */
    public static boolean ditherVideo(String inputPath, String outputPath, boolean includeAudio, String audioCodec, int audioBitrateKbps, int fps, int width) throws IOException, InterruptedException {
        if (inputPath == null || inputPath.isEmpty()) throw new IllegalArgumentException("inputPath required");
        String out = outputPath;
        if (out == null || out.isEmpty()) {
            File in = new File(inputPath);
            String name = in.getName();
            int dot = name.lastIndexOf('.');
            String base = (dot >= 0) ? name.substring(0, dot) : name;
            out = new File(in.getParentFile(), base + "_dithered.gif").getAbsolutePath();
        }

        if (out.toLowerCase().endsWith(".gif")) {
            int useFps = (fps > 0) ? fps : 15;
            int useWidth = (width > 0) ? width : -1;
            return convertMp4ToGif(inputPath, out, useFps, useWidth);
        }

        // For non-GIF output, run a video re-encode applying fps/scale and optional audio handling.
        // Use -2 to force the computed dimension to be divisible by 2 (libx264 requires even dims)
        String scale = (width > 0) ? width + ":-2:flags=lanczos" : "-2:flags=lanczos";
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(inputPath);
        if (fps > 0) {
            // build filter with fps and scale
            cmd.add("-vf");
            cmd.add("fps=" + fps + ",scale=" + scale);
        } else {
            cmd.add("-vf");
            cmd.add("scale=" + scale);
        }
        // set reasonable video codec
        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-preset"); cmd.add("fast");
        cmd.add("-crf"); cmd.add("23");

        if (includeAudio) {
            if (audioCodec != null && !audioCodec.isEmpty()) {
                cmd.add("-c:a"); cmd.add(audioCodec);
                if (audioBitrateKbps > 0) { cmd.add("-b:a"); cmd.add(audioBitrateKbps + "k"); }
            } else {
                // copy audio
                cmd.add("-c:a"); cmd.add("copy");
            }
        } else {
            cmd.add("-an");
        }

        cmd.add(out);

        ProcessResult r = execute(cmd);
        if (r.exitCode != 0) {
            System.err.println("ditherVideo ffmpeg failed (exit " + r.exitCode + "):\n" + r.output);
        }
        return r.exitCode == 0;
    }

    /**
     * Overload to match older callers: choice and scale multiplier.
     * `choice` is the dithering method (1-5) used by the app UI; for now it's informative only.
     */
    public static boolean ditherVideo(String inputPath, String outputPath, int choice, double scale, boolean includeAudio, String audioCodec, int audioKbps) throws IOException, InterruptedException {
        if (inputPath == null || inputPath.isEmpty()) throw new IllegalArgumentException("inputPath required");
        String out = outputPath;
        if (out == null || out.isEmpty()) {
            File in = new File(inputPath);
            String name = in.getName();
            int dot = name.lastIndexOf('.');
            String base = (dot >= 0) ? name.substring(0, dot) : name;
            out = new File(in.getParentFile(), base + "_dithered.mp4").getAbsolutePath();
        }

        // Informational: the Java-side dithering choice is not applied here; ffmpeg filters are used instead.
        if (choice < 1 || choice > 5) choice = 5;

        int useFps = 15;
        String scaleFilter;
        if (scale > 0 && Math.abs(scale - 1.0) > 1e-6) {
            // Use trunc(.../2)*2 to ensure resulting dimensions are even (divisible by 2)
            scaleFilter = "scale=trunc(iw*" + scale + "/2)*2:trunc(ih*" + scale + "/2)*2:flags=lanczos";
        } else {
            scaleFilter = "scale=iw:ih:flags=lanczos";
        }

        if (out.toLowerCase().endsWith(".gif")) {
            // Generate palette and GIF using scale expression and fps
            File palette = new File("palette.png");
            List<String> palCmd = new ArrayList<>();
            palCmd.add("ffmpeg"); palCmd.add("-y"); palCmd.add("-i"); palCmd.add(inputPath);
            palCmd.add("-vf"); palCmd.add("fps=" + useFps + "," + scaleFilter + ",palettegen");
            palCmd.add("palette.png");
            ProcessResult r1 = execute(palCmd);
            if (r1.exitCode != 0) {
                if (palette.exists()) palette.delete();
                System.err.println("ffmpeg palettegen failed (exit " + r1.exitCode + "). Output:\n" + r1.output);
                return false;
            }

            List<String> gifCmd = new ArrayList<>();
            gifCmd.add("ffmpeg"); gifCmd.add("-y"); gifCmd.add("-i"); gifCmd.add(inputPath); gifCmd.add("-i"); gifCmd.add("palette.png");
            gifCmd.add("-lavfi"); gifCmd.add("fps=" + useFps + "," + scaleFilter + "[x];[x][1:v]paletteuse=dither=bayer");
            gifCmd.add(out);

            ProcessResult r2 = execute(gifCmd);
            if (palette.exists()) palette.delete();
            if (r2.exitCode != 0) {
                System.err.println("ffmpeg gif generation failed (exit " + r2.exitCode + "). Output:\n" + r2.output);
            }
            return r2.exitCode == 0;
        }

        // Non-GIF: perform per-frame extraction, apply Java dithering, then reassemble
        return perFrameDither(inputPath, out, choice, scale, includeAudio, audioCodec, audioKbps, useFps);
    }

    private static boolean perFrameDither(String inputPath, String outputPath, int choice, double scale, boolean includeAudio, String audioCodec, int audioKbps, int fps) throws IOException, InterruptedException {
        Path tmpDir = Files.createTempDirectory("imager_frames_");
        File tmp = tmpDir.toFile();
        try {
            // Extract frames as PNG using fps and scaled dimensions (ensure even dims)
            String scaleFilter = (scale > 0 && Math.abs(scale - 1.0) > 1e-6)
                    ? "scale=trunc(iw*" + scale + "/2)*2:trunc(ih*" + scale + "/2)*2:flags=lanczos"
                    : "scale=trunc(iw/2)*2:trunc(ih/2)*2:flags=lanczos";

            String framePattern = new File(tmp, "frame_%06d.png").getAbsolutePath();
            List<String> extract = new ArrayList<>();
            extract.add("ffmpeg"); extract.add("-y"); extract.add("-i"); extract.add(inputPath);
            extract.add("-vf"); extract.add("fps=" + fps + "," + scaleFilter);
            extract.add("-vsync"); extract.add("0");
            extract.add(framePattern);
            ProcessResult rExtract = execute(extract);
            if (rExtract.exitCode != 0) {
                System.err.println("ffmpeg frame extraction failed (exit " + rExtract.exitCode + "):\n" + rExtract.output);
                return false;
            }

            // Load and process each frame
            File[] frames = tmp.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
            if (frames == null || frames.length == 0) {
                System.err.println("No frames extracted for per-frame dithering.");
                return false;
            }
            Arrays.sort(frames);
            for (int i = 0; i < frames.length; i++) {
                File f = frames[i];
                java.awt.image.BufferedImage img = Dithering.loadImage(f.getAbsolutePath());
                java.awt.image.BufferedImage outImg;
                switch (choice) {
                    case 1 -> outImg = Dithering.threshold(img, 128);
                    case 2 -> outImg = Dithering.randomDither(img);
                    case 3 -> outImg = Dithering.orderedBayer(img);
                    case 4 -> outImg = Dithering.orderedAvoidCluster(img);
                    case 5 -> outImg = Dithering.floydSteinberg(img);
                    default -> outImg = Dithering.floydSteinberg(img);
                }
                javax.imageio.ImageIO.write(outImg, "PNG", f);
            }

            // Assemble frames into a temporary video (no audio)
            File videoNoAudio = new File(tmp, "video_noaudio.mp4");
            List<String> assemble = new ArrayList<>();
            assemble.add("ffmpeg"); assemble.add("-y"); assemble.add("-framerate"); assemble.add(String.valueOf(fps));
            assemble.add("-i"); assemble.add(new File(tmp, "frame_%06d.png").getAbsolutePath());
            assemble.add("-c:v"); assemble.add("libx264"); assemble.add("-pix_fmt"); assemble.add("yuv420p");
            assemble.add(videoNoAudio.getAbsolutePath());
            ProcessResult rAssemble = execute(assemble);
            if (rAssemble.exitCode != 0) {
                System.err.println("ffmpeg assemble failed (exit " + rAssemble.exitCode + "):\n" + rAssemble.output);
                return false;
            }

            if (!includeAudio) {
                // Move/rename assembled video to outputPath
                Files.move(videoNoAudio.toPath(), new File(outputPath).toPath());
                return true;
            }

            // Extract/compress audio from original if needed
            File audioFile = new File(tmp, "audio.m4a");
            boolean audioOk = compressAudio(inputPath, audioFile.getAbsolutePath(), audioKbps > 0 ? audioKbps : 16, (audioCodec == null || audioCodec.isEmpty()) ? "aac" : audioCodec);
            if (!audioOk) {
                System.err.println("Audio compression/extraction failed; continuing without audio.");
                Files.move(videoNoAudio.toPath(), new File(outputPath).toPath());
                return true;
            }

            // Mux audio and video
            List<String> mux = new ArrayList<>();
            mux.add("ffmpeg"); mux.add("-y"); mux.add("-i"); mux.add(videoNoAudio.getAbsolutePath()); mux.add("-i"); mux.add(audioFile.getAbsolutePath());
            mux.add("-c:v"); mux.add("copy"); mux.add("-c:a"); mux.add("copy"); mux.add(outputPath);
            ProcessResult rMux = execute(mux);
            if (rMux.exitCode != 0) {
                System.err.println("ffmpeg mux failed (exit " + rMux.exitCode + "):\n" + rMux.output);
                return false;
            }

            return true;
        } finally {
            // best-effort cleanup
            try {
                Files.walk(tmpDir).map(Path::toFile).sorted((a,b)->b.getName().compareTo(a.getName())).forEach(File::delete);
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
