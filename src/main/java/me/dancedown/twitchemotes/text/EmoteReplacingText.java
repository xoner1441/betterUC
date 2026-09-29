package me.dancedown.twitchemotes.text;

import com.mojang.datafixers.util.Pair;
import me.dancedown.twitchemotes.TwitchEmotes;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public class EmoteReplacingText implements FormattedText {

    private final FormattedText original;

    public EmoteReplacingText(FormattedText original) {
        this.original = original;
    }

    /**
     * Just returns what the visitor of the original returns. The is neither called for
     * rendering, nor anywhere in the injection point.
     * @param contentConsumer The content consumer
     * @return An optional with the value to break or empty to keep iterating
     * @param <T> The value type
     */
    @Override
    public <T> @NonNull Optional<T> visit(@NonNull ContentConsumer<T> contentConsumer) {
        return original.visit(contentConsumer);
    }

    /**
     * Collects the different style-text pairs and replaces emote occurrences.
     * @param consumer The consumer that accepts the individual pairs
     * @param style The base style
     * @return An optional with the value to break or empty to keep iterating
     * @param <T> The value type
     */
    @Override
    public <T> @NonNull Optional<T> visit(@NonNull StyledContentConsumer<T> consumer, @NonNull Style style) {
        EmoteReplacingStyledStringBuilder styleBufferBuilder = new EmoteReplacingStyledStringBuilder();
        original.visit((s, text) -> {
            // collect the style and string of the components
            styleBufferBuilder.append(s, text);
            return Optional.empty();
        }, style);

        for(Pair<Style, StringBuilder> pair : styleBufferBuilder.build()) {
            Optional<T> result = consumer.accept(pair.getFirst(), pair.getSecond().toString());
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }
}

