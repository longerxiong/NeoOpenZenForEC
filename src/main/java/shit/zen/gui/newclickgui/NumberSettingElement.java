package shit.zen.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import shit.zen.gui.NewClickGui;
import shit.zen.gui.newclickgui.CategoryPanel;
import shit.zen.gui.newclickgui.SettingElement;
import shit.zen.render.FontStore;
import shit.zen.settings.impl.NumberSetting;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.utils.math.MathUtil;
import shit.zen.utils.misc.CursorUtil;
import shit.zen.utils.render.ColorUtil;
import shit.zen.utils.render.RenderUtil;

public class NumberSettingElement
extends SettingElement<NumberSetting> {
    private final SmoothAnimationTimer sliderTimer = new SmoothAnimationTimer();
    private boolean isTruncated;
    private boolean isHovered;
    private boolean isDragging;

    public NumberSettingElement(CategoryPanel categoryPanel, NumberSetting numberSetting) {
        super(categoryPanel, numberSetting);
    }

    @Override
    public float getHeight() {
        return 30.0f;
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        float dragRatio;
        this.isHovered = CursorUtil.isInBounds(mouseX, mouseY, this.x, this.y, 120.0f, this.getHeight());
        float sliderWidth = 108.0f;
        float sliderHeight = 6.0f;
        float sliderY = this.y + this.getHeight() - 8.0f;
        this.visibilityTimer.animate(this.setting.getVisibility().displayable() ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        this.visibilityTimer.tick();
        if (Mth.equal(alpha *= this.visibilityTimer.getValueF(), 0.0f)) {
            return;
        }
        float nameY = this.y + (this.getHeight() / 2.0f - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f + 1.0f;
        String name = this.setting.getName();
        if (FontStore.AXIFORMA_REGULAR_14.getStringWidth(name) > 78.0f) {
            name = name.substring(0, Math.min(10, name.length()));
            name = name + "...";
            this.isTruncated = true;
        }
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, this.x + 6.0f, nameY, ColorUtil.withAlpha(-1, alpha * 0.8f));
        String valueText = String.format(java.util.Locale.ROOT, "%.2f", this.setting.getValue().doubleValue());
        float min = this.setting.getMin().floatValue();
        float max = this.setting.getMax().floatValue();
        float range = max - min;
        float progress = range == 0.0f ? 0.0f : (this.setting.getValue().floatValue() - min) / range;
        progress = Mth.clamp(progress, 0.0f, 1.0f);
        this.sliderTimer.animate(progress, 0.2, Easings.EASE_OUT_POW2);
        this.sliderTimer.tick();
        if (this.isDragging) {
            NumberSetting numberSetting = this.setting;
            dragRatio = ((float)mouseX - (this.x + 6.0f)) / sliderWidth;
            double rawValue = numberSetting.getMin().floatValue() + (numberSetting.getMax().floatValue() - numberSetting.getMin().floatValue()) * dragRatio;
            double step = numberSetting.getStep().floatValue();
            double stepped = (double)Math.round(MathUtil.clamp(rawValue, numberSetting.getMin().floatValue(), numberSetting.getMax().floatValue()) / step) * step;
            numberSetting.setValue((double)Math.round(stepped * 1000.0) / 1000.0);
        }
        float fillAmount = this.sliderTimer.getValueF();
        float trackX = this.x + 6.0f;
        RenderUtil.drawFilledRect(poseStack, trackX, sliderY, sliderWidth, sliderHeight,
                ColorUtil.withAlpha(ColorUtil.fromRGB(66, 66, 66), alpha));
        if (fillAmount > 0.0f) {
            RenderUtil.drawFilledRect(poseStack, trackX, sliderY, Math.max(2.0f, sliderWidth * fillAmount), sliderHeight,
                    ColorUtil.withAlpha(CategoryPanel.ACCENT_COLOR, alpha));
        }
        float knobSize = 8.0f;
        float knobX = trackX + fillAmount * (sliderWidth - knobSize);
        RenderUtil.drawRoundedRect(poseStack, knobX, sliderY - 1.0f, knobSize, knobSize, knobSize / 2.0f,
                ColorUtil.withAlpha(-1, alpha));
        FontStore.AXIFORMA_BOLD_13.drawString(poseStack, valueText, this.x + 120.0f - FontStore.AXIFORMA_BOLD_13.getStringWidth(valueText) - 6.0f, nameY, ColorUtil.withAlpha(-1, alpha));
        if (this.isHovered && this.isTruncated) {
            this.parentPanel.setHoveredSettingElement(this);
            this.parentPanel.setTooltipText(this.setting.getName());
            this.parentPanel.setShowTooltip(true);
        } else if (this.parentPanel.getHoveredSettingElement() == this) {
            this.parentPanel.setShowTooltip(false);
            this.parentPanel.setHoveredSettingElement(null);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.setting.getVisibility().displayable()) {
            return false;
        }
        float sliderWidth = 108.0f;
        float sliderHeight = 6.0f;
        float sliderY = this.y + this.getHeight() - 8.0f;
        if (this.isHovered && CursorUtil.isInBounds((float)mouseX, (float)mouseY, this.x + 6.0f, sliderY - 2.0f, sliderWidth, sliderHeight + 4.0f)) {
            this.isDragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDragging = false;
        return false;
    }
}
