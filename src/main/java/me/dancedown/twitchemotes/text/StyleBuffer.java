package me.dancedown.twitchemotes.text;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Style;

import java.util.ArrayDeque;

public class StyleBuffer<T> extends ArrayDeque<Pair<Style, T>> {

    public StyleBuffer(StyleBuffer<T> styleBuffer) {
        super(styleBuffer);
    }

    public StyleBuffer() {
        super();
    }

    public void add(Style style, T value) {
        add(Pair.of(style, value));
    }

    /**
     * Polls from the Queue until it's empty and calls the consumer for it's contents
     * @param consumer The consumer which is called each iteration
     */
    public void forEachStylePoll(StyleBufferConsumer<T> consumer) {
        while(!this.isEmpty()) {
            Pair<Style, T> pair = this.poll();
            consumer.accept(pair.getFirst(), pair.getSecond());
        }
    }

    public interface StyleBufferConsumer<T> {
        void accept(Style style, T value);
    }

}

