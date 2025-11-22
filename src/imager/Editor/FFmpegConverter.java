package imager.Editor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * Small helper to run ffmpeg commands from Java.
 *
 * Usage notes:
 * - Requires `ffmpeg` installed and available on PATH.
 * - For GIF conversion we use a two-step palette approach for better colors.
 * - For audio compression you can choose codec and bitrate (kbps) — e.g. "aac" at 16 kbps
 *   for very small audio, or "libopus" for smaller WebM outputs.
 */
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

    /**
     * Try to read the video's frame rate (fps) via ffprobe. Returns -1 if unavailable.
     */
    public static double getVideoFps(String inputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffprobe", "-v", "0", "-select_streams", "v:0",
                    "-show_entries", "stream=r_frame_rate", "-of", "default=nokey=1:noprint_wrappers=1", inputFile);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (InputStreamReader isr = new InputStreamReader(p.getInputStream());
                 BufferedReader br = new BufferedReader(isr)) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
            }
            int rc = p.waitFor();
            if (rc != 0) return -1;
            String txt = out.toString().trim();
            if (txt.isEmpty()) return -1;
            // typically a fraction like 30000/1001
            if (txt.contains("/")) {
                String[] parts = txt.split("/");
                double a = Double.parseDouble(parts[0]);
                double b = Double.parseDouble(parts[1]);
                return a / b;
            } else {
                return Double.parseDouble(txt);
            }
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Process a video by extracting frames, applying a per-frame image dithering method, and reassembling.
     * Returns true on success. Requires ffmpeg on PATH.
     */
    public static boolean ditherVideo(String inputMp4, String outputMp4, int methodChoice, double scale,
                                      boolean includeAudio, String audioCodec, int audioKbps) throws IOException, InterruptedException {
        Path tmpDir = Files.createTempDirectory("imager_frames_");
        try {
            // 1) extract frames as PNGs
            List<String> extractCmd = new ArrayList<>(Arrays.asList(
                    "ffmpeg", "-y", "-i", inputMp4,
                    tmpDir.resolve("frame%06d.png").toAbsolutePath().toString()
            ));
            ProcessBuilder pbEx = new ProcessBuilder(extractCmd);
            pbEx.redirectErrorStream(true);
            Process pEx = pbEx.start();
            consumeStream(pEx.getInputStream());
            int rcEx = pEx.waitFor();
            if (rcEx != 0) return false;

            // 2) find extracted frames
            List<Path> frames = new ArrayList<>();
            try (var s = Files.list(tmpDir)) {
                s.filter(p -> p.getFileName().toString().matches("frame\\d{6}\\.png"))
                 .sorted(Comparator.naturalOrder())
                 .forEach(frames::add);
            }
            if (frames.isEmpty()) return false;

            // 3) process frames one by one
            int idx = 1;
            for (Path f : frames) {
                BufferedImage img = null;
                try {
                    img = Dithering.loadImage(f.toAbsolutePath().toString());
                } catch (IOException e) {
                    return false;
                }
                if (scale != 1.0) img = Dithering.resize(img, scale);
                BufferedImage outImg;
                switch (methodChoice) {
                    case 1:
                        outImg = Dithering.threshold(img, 128);
                        break;
                    case 2:
                        outImg = Dithering.randomDither(img);
                        break;
                    case 3:
                        outImg = Dithering.orderedBayer(img);
                        break;
                    case 4:
                        outImg = Dithering.orderedAvoidCluster(img);
                        break;
                    case 5:
                    default:
                        outImg = Dithering.floydSteinberg(img);
                        break;
                }
                String outName = String.format("dithered%06d.png", idx);
                Path outPath = tmpDir.resolve(outName);
                ImageIO.write(outImg, "PNG", outPath.toFile());
                idx++;
            }

            // 4) assemble video from dithered frames
            double fpsD = getVideoFps(inputMp4);
            int fps = (int) Math.max(1, Math.round(fpsD > 0 ? fpsD : 15));

            Path noAudioMp4 = tmpDir.resolve("dithered_noaudio.mp4");
            List<String> assembleCmd = new ArrayList<>(Arrays.asList(
                    "ffmpeg", "-y", "-framerate", String.valueOf(fps), "-i",
                    tmpDir.resolve("dithered%06d.png").toAbsolutePath().toString(),
                    "-c:v", "libx264", "-pix_fmt", "yuv420p",
                    noAudioMp4.toAbsolutePath().toString()
            ));
            ProcessBuilder pbAsm = new ProcessBuilder(assembleCmd);
            pbAsm.redirectErrorStream(true);
            Process pAsm = pbAsm.start();
            consumeStream(pAsm.getInputStream());
            int rcAsm = pAsm.waitFor();
            if (rcAsm != 0) return false;

            if (!includeAudio) {
                // move no-audio file to output
                Files.move(noAudioMp4, Path.of(outputMp4), StandardCopyOption.REPLACE_EXISTING);
                return true;
            }

            // 5) compress audio from original and merge
            Path audioTmp = tmpDir.resolve("audio_compressed.m4a");
            boolean audioOk = compressAudio(inputMp4, audioTmp.toAbsolutePath().toString(), audioKbps, audioCodec);
            if (!audioOk) {
                // fallback: copy original audio
                List<String> mergeCmd = new ArrayList<>(Arrays.asList(
                        "ffmpeg", "-y", "-i", noAudioMp4.toAbsolutePath().toString(), "-i", inputMp4,
                        "-map", "0:v", "-map", "1:a?", "-c", "copy", outputMp4
                ));
                ProcessBuilder pbMerge = new ProcessBuilder(mergeCmd);
                pbMerge.redirectErrorStream(true);
                Process pMerge = pbMerge.start();
                consumeStream(pMerge.getInputStream());
                int rcMerge = pMerge.waitFor();
                if (rcMerge != 0) return false;
                return true;
            }

            // merge compressed audio with video
            List<String> mergeCmd = new ArrayList<>(Arrays.asList(
                    "ffmpeg", "-y", "-i", noAudioMp4.toAbsolutePath().toString(), "-i", audioTmp.toAbsolutePath().toString(),
                    "-map", "0:v", "-map", "1:a", "-c", "copy", outputMp4
            ));
            ProcessBuilder pbMerge = new ProcessBuilder(mergeCmd);
            pbMerge.redirectErrorStream(true);
            Process pMerge = pbMerge.start();
            consumeStream(pMerge.getInputStream());
            int rcMerge = pMerge.waitFor();
            return rcMerge == 0;
        } finally {
            // cleanup temp dir
            try {
                Files.walk(Path.of(tmpDir.toString()))
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException ex) {
                // ignore cleanup errors
            }
        }
    }

    /**
     * Convert an MP4 to an animated GIF using a palette for better quality.
     *
     * @param inputMp4 path to source mp4
     * @param outputGif path to output gif
     * @param fps frames per second (e.g. 10-20)
     * @param width target width in pixels (use -1 to keep original; pass >0)
     * @return true on success
     */
    public static boolean convertMp4ToGif(String inputMp4, String outputGif, int fps, int width) throws IOException, InterruptedException {
        String scale = (width > 0) ? width + ":-1:flags=lanczos" : "-1:flags=lanczos";

        // palette file in working directory (deleted after)
        File palette = new File("palette.png");

        List<String> palCmd = new ArrayList<>(Arrays.asList(
                "ffmpeg", "-y", "-i", inputMp4,
                "-vf", "fps=" + fps + ",scale=" + scale + ",palettegen",
                "palette.png"
        ));

        ProcessBuilder pb1 = new ProcessBuilder(palCmd);
        pb1.redirectErrorStream(true);
        Process p1 = pb1.start();
        consumeStream(p1.getInputStream());
        int rc1 = p1.waitFor();
        if (rc1 != 0) {
            if (palette.exists()) palette.delete();
            return false;
        }

        List<String> gifCmd = new ArrayList<>(Arrays.asList(
                "ffmpeg", "-y", "-i", inputMp4, "-i", "palette.png",
                "-lavfi", "fps=" + fps + ",scale=" + scale + "[x];[x][1:v]paletteuse=dither=bayer",
                outputGif
        ));

        ProcessBuilder pb2 = new ProcessBuilder(gifCmd);
        pb2.redirectErrorStream(true);
        Process p2 = pb2.start();
        consumeStream(p2.getInputStream());
        int rc2 = p2.waitFor();

        if (palette.exists()) palette.delete();
        return rc2 == 0;
    }

    /**
     * Re-encode (compress) the audio track of a file to a very small size.
     * This will drop video by default (useful when extracting audio only).
     *
     * @param inputFile source file (can be mp4)
     * @param outputFile destination (e.g. .m4a, .mp3, .webm depending on codec)
     * @param bitrateKbps target bitrate in kbps (e.g. 16 for very low quality)
     * @param codec audio codec: e.g. "aac", "libmp3lame", "libopus"
     * @return true on success
     */
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

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        consumeStream(p.getInputStream());
        int rc = p.waitFor();
        return rc == 0;
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
}
