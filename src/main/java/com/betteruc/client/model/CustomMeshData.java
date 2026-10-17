package com.betteruc.client.model;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Compact, dependency-free mesh data used by the client-side self model. */
public final class CustomMeshData {
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_VERTICES = 100_000;
    private static final int MAX_INDICES = 600_000;

    private final float[] positions;
    private final float[] normals;
    private final float[] uvs;
    private final int[] colors;
    private final int[] indices;

    private CustomMeshData(float[] positions, float[] normals, float[] uvs, int[] colors, int[] indices) {
        this.positions = positions;
        this.normals = normals;
        this.uvs = uvs;
        this.colors = colors;
        this.indices = indices;
    }

    public static CustomMeshData read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Mesh resource is missing");
        try (DataInputStream data = new DataInputStream(new BufferedInputStream(input))) {
            byte[] magic = data.readNBytes(4);
            if (!Arrays.equals(magic, new byte[]{'B', 'U', 'M', '1'})) {
                throw new IOException("Invalid betterUC mesh header");
            }

            int version = data.readInt();
            int vertexCount = data.readInt();
            int indexCount = data.readInt();
            if (version != FORMAT_VERSION && version != 2) {
                throw new IOException("Unsupported mesh version: " + version);
            }
            if (vertexCount <= 0 || vertexCount > MAX_VERTICES) {
                throw new IOException("Invalid mesh vertex count: " + vertexCount);
            }
            if (indexCount <= 0 || indexCount > MAX_INDICES || indexCount % 3 != 0) {
                throw new IOException("Invalid mesh index count: " + indexCount);
            }

            float[] positions = readFloats(data, vertexCount * 3);
            float[] normals = readFloats(data, vertexCount * 3);
            float[] uvs = readFloats(data, vertexCount * 2);
            int[] colors = new int[vertexCount];
            if (version >= 2) {
                for (int i = 0; i < vertexCount; i++) colors[i] = data.readInt();
            } else {
                Arrays.fill(colors, 0xFFFFFFFF);
            }
            int[] indices = new int[indexCount];
            for (int i = 0; i < indexCount; i++) {
                int index = data.readInt();
                if (index < 0 || index >= vertexCount) {
                    throw new IOException("Mesh index outside vertex range: " + index);
                }
                indices[i] = index;
            }
            return new CustomMeshData(positions, normals, uvs, colors, indices);
        }
    }

    private static float[] readFloats(DataInputStream data, int count) throws IOException {
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            float value = data.readFloat();
            if (!Float.isFinite(value)) throw new IOException("Mesh contains a non-finite value");
            values[i] = value;
        }
        return values;
    }

    public int vertexCount() {
        return positions.length / 3;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    public float[] positions() {
        return positions;
    }

    public float[] normals() {
        return normals;
    }

    public float[] uvs() {
        return uvs;
    }

    public int[] colors() {
        return colors;
    }

    public int[] indices() {
        return indices;
    }
}
