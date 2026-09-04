package com.betteruc.client.clips;

import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.JarURLConnection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Loads the actual bundled runtime, but opens no GPU session, screen capture or audio device. */
@EnabledOnOs(OS.WINDOWS)
class ClipNativeRuntimeTest {
    @ParameterizedTest
    @ValueSource(strings = {"h264_nvenc", "h264_amf", "h264_qsv"})
    void allHardwareEncodersAndTheirConfiguredOptionsExist(String name) {
        var codec = avcodec_find_encoder_by_name(name);
        assertNotNull(codec, name + " must be bundled, regardless of installed GPU");
        assertFalse(codec.isNull());
        var context = avcodec_alloc_context3(codec);
        var options = new AVDictionary(null);
        try {
            assertFalse(context.isNull());
            HardwareClipEncoder.configureOptions(name, options);
            assertTrue(av_dict_count(options) >= 3);
            assertEquals(0, av_opt_set_dict2(context.priv_data(), options, 0), name + " accepts configured values");
            assertEquals(0, av_dict_count(options), name + " consumes all configured options");
        } finally {
            av_dict_free(options);
            avcodec_free_context(context);
        }
    }

    @Test void pinnedRuntimeHasCompatibleAbiAndNoGplOrNonfreeSwitch() {
        assertEquals("n8.0.1-66-g27b8d1a017-20260228", av_version_info().getString());
        assertEquals(62, avcodec_version() >>> 16);
        assertEquals(60, avutil_version() >>> 16);
        assertEquals("LGPL version 3 or later", avcodec_license().getString());
        String flags = avcodec_configuration().getString();
        assertTrue(flags.contains("--enable-amf"));
        assertFalse(flags.contains("--enable-gpl"));
        assertFalse(flags.contains("--enable-nonfree"));
        assertNotNull(avcodec_find_encoder_by_name("aac"));
        assertNotNull(avcodec_find_decoder(AV_CODEC_ID_H264));
        assertNotNull(avcodec_find_decoder(AV_CODEC_ID_AAC));
    }

    @Test void nativeJarContainsOnlyFiveRuntimeDllsAndTheirFiveJniBridges() throws Exception {
        var resources = Collections.list(getClass().getClassLoader()
                .getResources("org/bytedeco/ffmpeg/windows-x86_64/avcodec-62.dll"));
        assertEquals(1, resources.size(), "No old runtime may remain on the classpath");
        var connection = (JarURLConnection) resources.getFirst().openConnection();
        connection.setUseCaches(false);
        try (var jar = connection.getJarFile()) {
            var names = jar.stream().map(e -> e.getName()).toList();
            assertTrue(names.stream().noneMatch(n -> n.endsWith(".exe") || n.endsWith(".lib")));
            Set<String> dlls = names.stream().filter(n -> n.endsWith(".dll"))
                    .map(n -> n.substring(n.lastIndexOf('/') + 1)).collect(Collectors.toSet());
            assertEquals(Set.of("avcodec-62.dll", "avformat-62.dll", "avutil-60.dll", "swresample-6.dll",
                    "swscale-9.dll", "jniavcodec.dll", "jniavformat.dll", "jniavutil.dll", "jniswresample.dll",
                    "jniswscale.dll"), dlls);
            assertTrue(names.contains("META-INF/licenses/clips/ffmpeg-runtime.properties"));
            assertTrue(names.contains("META-INF/licenses/clips/ffmpeg-runtime-LICENSE.txt"));
            assertTrue(names.stream().noneMatch(n -> n.toLowerCase().contains("amfrt64")), "Driver DLL not bundled");
        }
    }
}
