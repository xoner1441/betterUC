package me.dancedown.twitchemotes.emote.image;

import com.mojang.blaze3d.platform.NativeImage;
import me.dancedown.twitchemotes.emote.type.EmoteFormat;
import me.dancedown.twitchemotes.exception.EmoteParseException;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EmoteImageParser {

    public static final String PREFIX = "emotes/";

    private final String name;
    private final Identifier id;
    private final int scale;

    public EmoteImageParser(String id, String name, int scale) {
        this.name = name;
        this.id = Identifier.fromNamespaceAndPath("twitchemotes",
                PREFIX + name.toLowerCase().replaceAll("[^a-z0-9._-]", "_") + "-" + id.toLowerCase());
        this.scale = scale;
    }

    /**
     * Parses the bytes of a specific image type to an EmotesImages Object
     * @param bytes The data of the image
     * @param format The format of the image
     * @return An EmoteImages Object containing the frame(s)
     * @throws IOException If image couldn't be parsed
     */
    @NotNull
    public EmoteImage parse(byte[] bytes, EmoteFormat format) throws IOException {
        return switch (format) {
            case GIF -> parseGIF(bytes);
            case PNG -> parsePNG(bytes);
            case WEBP -> throw new IOException("WEBP is not enabled in the embedded betterUC runtime");
        };
    }

    /**
     * Creates a new overlay image from 2 emote images
     * @param baseImage The base image
     * @param overlayImage The overlay image that is drawn over the base image
     * @return A new OverlayEmoteImage which is a composition of the given images
     * @throws EmoteParseException If image couldn't be created
     */
    public static OverlayEmoteImage parseOverlay(String emoteNames, EmoteImage baseImage, EmoteImage overlayImage) throws EmoteParseException {
        String path = EmoteImageParser.PREFIX
                + baseImage.imageId().getPath().substring(EmoteImageParser.PREFIX.length())
                + overlayImage.imageId().getPath().substring(EmoteImageParser.PREFIX.length());
        Identifier identifier = Identifier.fromNamespaceAndPath(baseImage.imageId().getNamespace(), path);
        OverlayEmoteImage image = EmoteImageFactory.createOverlayEmoteImage(baseImage, overlayImage, identifier, emoteNames);
        if(image == null)
            throw new EmoteParseException();
        return image;
    }

    /**
     * Parses the bytes of a GIF to a EmoteImages object
     * @param data The bytes of the GIF
     * @return An EmoteImages Object
     * @throws IOException If GIF couldn't be parsed
     */
    @NotNull
    public EmoteImage parseGIF(byte[] data) throws IOException {
        ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data));
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext())
            throw new IOException("No gif image found for " + name);

        ImageReader reader = readers.next();
        reader.setInput(stream, false);

        // -------- Logical Screen Size --------
        int width = -1;
        int height = -1;

        IIOMetadata streamMetadata = reader.getStreamMetadata();
        if (streamMetadata != null) {
            Node root = streamMetadata.getAsTree(streamMetadata.getNativeMetadataFormatName());
            NodeList nodes = root.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if ("LogicalScreenDescriptor".equals(node.getNodeName())) {
                    NamedNodeMap attrs = node.getAttributes();
                    width = Integer.parseInt(attrs.getNamedItem("logicalScreenWidth").getNodeValue());
                    height = Integer.parseInt(attrs.getNamedItem("logicalScreenHeight").getNodeValue());
                    break;
                }
            }
        } else
            throw new IOException("Couldn't load metadata of the GIF for " + name);

        // -------- Frames & Durations --------
        List<NativeImage> frames = new ArrayList<>();
        List<Integer> durationsMs = new ArrayList<>();

        int frameCount = reader.getNumImages(true);

        for (int i = 0; i < frameCount; i++) {
            BufferedImage frame = reader.read(i);
            IIOMetadata metadata = reader.getImageMetadata(i);
            int delayMs = extractGIFDelay(metadata);
            durationsMs.add(delayMs);
            frames.add(convertToNativeImage(frame));
        }
        reader.dispose();
        stream.close();

        EmoteImage image;
        if(frames.isEmpty())
            throw new IOException("Couldn't load GIF frames for emote " + name);
        else if (frameCount == 1) {
            image = EmoteImageFactory.createStaticEmoteImage(frames.getFirst(), id, name, width, height, scale);
            if (image == null)
                throw new IOException("Couldn't load static GIF image for emote " + name);
        } else {
            image = EmoteImageFactory.createAnimatedEmoteImage(frames, durationsMs, id, name, width, height, scale);
            if (image == null)
                throw new IOException("Couldn't load animated GIF image for emote " + name);
        }
        return image;
    }

    /**
     * Extracts the duration of a frame from the GIF metadata
     * @param metadata The metadata of the frame
     * @return The delay in ms. If no delay is provided, returns 100ms
     */
    private int extractGIFDelay(IIOMetadata metadata) {
        String format = metadata.getNativeMetadataFormatName();
        Node root = metadata.getAsTree(format);

        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);

            if ("GraphicControlExtension".equals(node.getNodeName())) {
                NamedNodeMap attrs = node.getAttributes();
                int delayCs = Integer.parseInt(attrs.getNamedItem("delayTime").getNodeValue());
                return Math.max(10, delayCs * 10);
            }
        }

        // fallback to 0.1s if no delay is provided
        return 100;
    }

    /**
     * Converts a BufferedImage to a NativeImage
     * @param frame The BufferedImage to convert
     * @return The NativeImage
     * @throws IOException If BufferedImage couldn't be converted
     */
    private NativeImage convertToNativeImage(BufferedImage frame) throws IOException {
        ByteArrayOutputStream oS = new ByteArrayOutputStream();
        ImageIO.write(frame, "png", oS);
        return NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(oS.toByteArray()));
    }

    /**
     * Converts the bytes of a PNG to a EmoteImages Object with 1 frame
     * @param bytes The data of the PNG
     * @return An EmoteImages Object with 1 frame
     * @throws IOException If image couldn't be parsed
     */
    private EmoteImage parsePNG(byte[] bytes) throws IOException {
        NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
        EmoteImage emoteImage = EmoteImageFactory.createStaticEmoteImage(image, id, name, image.getWidth(), image.getHeight(), scale);
        if(emoteImage == null)
            throw new IOException("Couldn't load PNG emote image for " + name);
        return emoteImage;
    }

}

