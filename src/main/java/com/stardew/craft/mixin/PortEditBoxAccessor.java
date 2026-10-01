package com.stardew.craft.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.BiFunction;

/** PORT(1.20.1): private EditBox state read by {@code PortEditBox}'s 1.21.1 renderer. */
@Mixin(EditBox.class)
public interface PortEditBoxAccessor {
    @Accessor("font") Font port$font();
    @Accessor("value") String port$value();
    @Accessor("displayPos") int port$displayPos();
    @Accessor("cursorPos") int port$cursorPos();
    @Accessor("highlightPos") int port$highlightPos();
    @Accessor("isEditable") boolean port$isEditable();
    @Accessor("bordered") boolean port$bordered();
    @Accessor("textColor") int port$textColor();
    @Accessor("textColorUneditable") int port$textColorUneditable();
    @Accessor("formatter") BiFunction<String, Integer, FormattedCharSequence> port$formatter();
    @Accessor("hint") Component port$hint();
    @Accessor("suggestion") String port$suggestion();
    @Invoker("onValueChange") void port$onValueChange(String value);
    @Invoker("getMaxLength") int port$getMaxLength();
    @Invoker("renderHighlight") void port$renderHighlight(GuiGraphics graphics, int minX, int minY, int maxX, int maxY);
}
