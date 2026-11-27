package cn.furina.crystalmultiplier.client.gui;

import mekanism.client.gui.element.GuiElement;

/**
 * Tiny bridge that lets a mixin into Mekanism's GUI windows add a child element.
 *
 * <p>{@link GuiElement#addChild} is protected and generic, so neither a plain cast nor a lambda can
 * reach it. {@code GuiElementAccessor} (a mixin on {@link GuiElement}) implements this interface and
 * exposes the call; everything on the other side just casts to it.</p>
 */
public interface CrystalChildAdder {

    void crystal$addChild(GuiElement element);
}
