package cn.furina.crystalmultiplier.mixin;

import cn.furina.crystalmultiplier.client.gui.CrystalChildAdder;
import mekanism.client.gui.element.GuiElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes {@link GuiElement#addChild} so windows outside this mod can add their own widgets. */
@Mixin(value = GuiElement.class, remap = false)
public abstract class GuiElementAccessor implements CrystalChildAdder {

    @Shadow(remap = false)
    protected abstract <ELEMENT extends GuiElement> ELEMENT addChild(ELEMENT element);

    @Override
    public void crystal$addChild(GuiElement element) {
        this.addChild(element);
    }
}
