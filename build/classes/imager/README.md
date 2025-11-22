Simple Imager

- **Requirement:** `ffmpeg` is required for video input processing and per-frame dithering.

Quick checks

- Verify ffmpeg is installed and on PATH:

```bash
ffmpeg -version
which ffmpeg
```

Installation (Linux - Debian/Ubuntu):

```bash
sudo apt update
sudo apt install ffmpeg
```

Fedora/CentOS (if available in repos):

```bash
sudo dnf install ffmpeg
```

Arch Linux:

```bash
sudo pacman -S ffmpeg
```

macOS (Homebrew):

```bash
brew install ffmpeg
```

Windows (scoop/chocolatey or official builds):

- Chocolatey: `choco install ffmpeg`
- Scoop: `scoop install ffmpeg`

Troubleshooting

- If `ffmpeg -version` works but Imager still reports "ffmpeg not available" or video processing fails:
  - Ensure the IDE/launcher has the same `PATH` as your shell. Restart the IDE after installing `ffmpeg`.
  - Run the program from a terminal where `ffmpeg` is available.
  - If conversion fails (non-zero exit code), inspect the `ffmpeg` output by running the same commands the app uses (palette generation + paletteuse). See `Editor/FFmpegConverter.java` for exact commands.

Want me to also change the app to print ffmpeg's error output on failure? I can add a small change to `Editor/FFmpegConverter.java` to capture and log ffmpeg stderr when rc != 0 for easier diagnostics.