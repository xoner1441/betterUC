package com.betteruc.client.clips;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Hidden synthetic GPU test, no game/desktop capture. Uses the recording pipeline's actual shader resources. */
@EnabledIfSystemProperty(named = "betteruc.clipHardwareTest", matches = "true")
class ClipCaptureGpuTest {
    @Test void transparentScenePixelsKeepTheirRgbWhenDownscaled() throws Exception {
        assertTrue(glfwInit(), "GLFW initialization");
        long window = 0;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(16, 16, "betterUC synthetic clip test", 0, 0);
            assertNotEquals(0, window);
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            verifyCopy();
        } finally {
            if (window != 0) {
                glfwMakeContextCurrent(0);
                GL.setCapabilities(null);
                glfwDestroyWindow(window);
            }
            glfwTerminate();
        }
    }

    private void verifyCopy() throws IOException {
        var pipeline = ClipCapturePipeline.COLOR_COPY;
        int vertex = 0, fragment = 0, program = 0, vao = 0, input = 0, output = 0, fbo = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, shaderSource(pipeline.getVertexShader(), ".vsh"));
            fragment = compile(GL_FRAGMENT_SHADER, shaderSource(pipeline.getFragmentShader(), ".fsh"));
            program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "InSampler"), 0);
            vao = glGenVertexArrays();
            glBindVertexArray(vao);

            // Four sky-blue strips with different alpha; every pair of input pixels becomes one output pixel.
            int[] alphas = {0, 64, 128, 255};
            ByteBuffer source = ByteBuffer.allocateDirect(8 * 4 * 4);
            for (int y = 0; y < 4; y++) for (int x = 0; x < 8; x++) {
                source.put((byte) 96).put((byte) 160).put((byte) 224).put((byte) alphas[x / 2]);
            }
            source.flip();
            input = texture(8, 4, source);
            output = texture(4, 2, null);
            fbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, output, 0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
            glViewport(0, 0, 4, 2);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, input);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glDisable(GL_DITHER);

            // Reproduce the old outline-overlay path: transparent RGB turns into black.
            clear();
            glEnable(GL_BLEND);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
            glColorMask(true, true, true, false);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            ByteBuffer old = readPixels();
            assertEquals(0, old.get(0) & 255);
            assertTrue((old.get(4) & 255) < 30);

            // Apply the production pipeline's new color write state.
            clear();
            ColorTargetState target = pipeline.getColorTargetState();
            assertTrue(target.blendFunction().isEmpty());
            glDisable(GL_BLEND);
            glColorMask(target.writeRed(), target.writeGreen(), target.writeBlue(), target.writeAlpha());
            glDrawArrays(GL_TRIANGLES, 0, 3);
            ByteBuffer corrected = readPixels();
            for (int pixel = 0; pixel < 8; pixel++) {
                assertEquals(96, corrected.get(pixel * 4) & 255, "Red, pixel " + pixel);
                assertEquals(160, corrected.get(pixel * 4 + 1) & 255, "Green, pixel " + pixel);
                assertEquals(224, corrected.get(pixel * 4 + 2) & 255, "Blue, pixel " + pixel);
                assertEquals(255, corrected.get(pixel * 4 + 3) & 255, "Video alpha must remain opaque");
            }
            assertEquals(GL_NO_ERROR, glGetError());
            System.out.println("GPU regression confirmed: old alpha blend produces black/dark pixels; RGB copy preserves every strip after linear downscale.");
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            if (fbo != 0) glDeleteFramebuffers(fbo);
            if (output != 0) glDeleteTextures(output);
            if (input != 0) glDeleteTextures(input);
            if (vao != 0) glDeleteVertexArrays(vao);
            if (program != 0) glDeleteProgram(program);
            if (fragment != 0) glDeleteShader(fragment);
            if (vertex != 0) glDeleteShader(vertex);
        }
    }

    private static void clear() {
        glColorMask(true, true, true, true);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private static ByteBuffer readPixels() {
        ByteBuffer result = ByteBuffer.allocateDirect(4 * 2 * 4);
        glReadPixels(0, 0, 4, 2, GL_RGBA, GL_UNSIGNED_BYTE, result);
        return result;
    }

    private static int texture(int width, int height, ByteBuffer pixels) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return id;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
            String error = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            fail(error);
        }
        return shader;
    }

    private static String shaderSource(Identifier id, String extension) throws IOException {
        String path = "/assets/" + id.getNamespace() + "/shaders/" + id.getPath() + extension;
        try (var stream = ClipCaptureGpuTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing shader resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
