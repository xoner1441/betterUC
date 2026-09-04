# Clip prototype: third-party components

The optional Windows x64 clip recorder uses dynamically linked native libraries
inside the Minecraft process. It does not launch ffmpeg.exe/ffprobe.exe or download
recording components at runtime. JavaCPP extracts bundled DLLs to its local cache
in order to load them. No desktop video capture or upload is used.

Optional audio uses Windows WASAPI through the JNA 5.17.0
libraries already provided by Minecraft 26.2. No additional JNA artifact, custom
capture DLL or executable is bundled. Game-only mode targets the current Minecraft
process ID in INCLUDE_TARGET_PROCESS_TREE mode (requires Windows build 20348+).
Separately consented system-output mode uses render-endpoint loopback, instead of
also capturing the game process. Independently opt-in microphone capture uses an
input endpoint without loopback. Devices are enumerated with MMDevice; explicit
unavailable devices never silently fall back to a different input or output.
The audio options/device IDs remain local and cannot be changed by cloud sync.
API reference: https://learn.microsoft.com/en-us/samples/microsoft/windows-classic-samples/applicationloopbackaudio-sample/
Endpoint reference: https://learn.microsoft.com/en-us/windows/win32/coreaudio/loopback-recording
JNA source/license: https://github.com/java-native-access/jna/tree/5.17.0

Audio is encoded to AAC-LC by the already bundled FFmpeg library at export time.
The prohibition on CPU fallback below refers to video encoding, not AAC audio.

Bundled versions:

- JavaCPP 1.5.13 (Apache License 2.0 or GPLv2 with Classpath Exception):
  https://github.com/bytedeco/javacpp/tree/1.5.13
- JavaCPP FFmpeg preset 8.0.1-1.5.13 (Java/JNI bindings: Apache License 2.0 or GPL with Classpath Exception):
  https://github.com/bytedeco/javacpp-presets/tree/1.5.13/ffmpeg
  Only five JNI bridges are taken from the Bytedeco Windows artifact; its FFmpeg
  runtime DLLs are not packaged because that build does not contain AMD AMF.
- FFmpeg n8.0.1-66-g27b8d1a017-20260228, LGPL version 3 or later, BtbN Windows x64
  LGPL shared-library build. Includes AMF, NVENC and QSV; not the GPL/nonfree variant.
  Runtime license and configure flags are checked in ClipNativeRuntimeTest via
  avcodec_license() and avcodec_configuration(). Sources for this exact revision:
  https://github.com/FFmpeg/FFmpeg/tree/27b8d1a017
  Build recipe and dependency revisions:
  https://github.com/BtbN/FFmpeg-Builds/tree/autobuild-2026-02-28-12-59
  Release: https://github.com/BtbN/FFmpeg-Builds/releases/tag/autobuild-2026-02-28-12-59
  Download URL and SHA-256 are locked in third-party/clips/ffmpeg-runtime.properties.
  gradle/clip-natives.gradle verifies the hash for downloaded AND cached archives.
  This is a build-time download, never a Minecraft/runtime download. Offline builds
  work once the verified archive is in the build directory's clip-native-downloads.
  License and redistribution information: https://ffmpeg.org/legal.html
- AMD AMF headers (MIT), used by the upstream FFmpeg build; no AMD driver is bundled:
  https://github.com/GPUOpen-LibrariesAndSDKs/AMF/tree/d0b3e6dd544a5f207bb6a12a1ecb98532491176a

The JavaCPP/preset and FFmpeg license texts are included under META-INF/licenses/clips.
The original runtime archive's LICENSE.txt and the pinned runtime properties are
also included in the nested native JAR. The AMD header license is included separately.
The GPLv3 text is included because LGPLv3 incorporates its terms; this does not
mean the optional GPL FFmpeg build is used. The native
artifact uses an explicit allowlist: avutil, swresample, avcodec, avformat, swscale
and their five JNI bridges. No avdevice/avfilter, CLI executables, headers, import
libraries or native-image CLI metadata are packaged. The runtime DLLs are not
modified. No optional GPL FFmpeg artifact or x264 is included. JavaCPP bindings
and runtime share FFmpeg 8.0's ABI; native tests cover codec presence, options,
video encode/decode/remux and AAC mixing/synchronization before distribution.
The corresponding source/build recipes above and upstream notices must remain
available with any public distribution. Review the full third-party distribution
requirements before turning this local prototype into a public release.

Hardware encoding requires a compatible installed graphics driver. NVENC, AMF and
QSV are all built into this pinned artifact starting in beta 11. They are tested in
that order, including a short synthetic encode/flush, before recording starts.
AMF option/presence tests do not prove successful recording on a Radeon: an actual
AMD tester must still validate video, performance and sound in Minecraft.
No driver is installed or downloaded by the mod. Unsupported encoders fail closed
rather than falling back to CPU encoding.
