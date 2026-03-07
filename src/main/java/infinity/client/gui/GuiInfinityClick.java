package infinity.client.gui;

import infinity.client.InfinityClient;
import infinity.client.module.Category;
import infinity.client.module.Module;
import infinity.client.setting.ModeSetting;
import infinity.client.setting.Setting;
import infinity.client.setting.SliderSetting;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;

public class GuiInfinityClick extends GuiScreen {
    private final InfinityClient client;
    private int scroll;

    public GuiInfinityClick(InfinityClient client) {
        this.client = client;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = 20;
        drawRect(10, 10, width - 10, height - 10, 0xCC10141A);
        fontRenderer.drawStringWithShadow("∞  Infinity Client  (I)(C)", 24, 18, 0xAFAFFF);
        for (Category c : Category.values()) {
            fontRenderer.drawStringWithShadow(c.name(), x, 40, 0xFFFFFF);
            int y = 54 - scroll;
            List<Module> mods = client.byCategory(c);
            for (Module m : mods) {
                int color = m.isEnabled() ? 0xCC23384A : 0xCC1A1F27;
                drawRect(x, y, x + 150, y + 14, color);
                fontRenderer.drawString(m.getName(), x + 4, y + 3, 0xEDEDED);
                y += 16;
                if (m.isEnabled()) {
                    for (Setting s : m.getSettings()) {
                        drawRect(x + 6, y, x + 146, y + 12, 0x99303A46);
                        if (s instanceof SliderSetting) {
                            SliderSetting sl = (SliderSetting) s;
                            fontRenderer.drawString(sl.getName() + ": " + String.format("%.2f", sl.getValue()), x + 8, y + 2, 0xB0E0FF);
                        } else if (s instanceof ModeSetting) {
                            ModeSetting md = (ModeSetting) s;
                            fontRenderer.drawString(md.getName() + ": " + md.getMode(), x + 8, y + 2, 0xFFDFB0);
                        }
                        y += 13;
                    }
                }
            }
            x += 170;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int x = 20;
        for (Category c : Category.values()) {
            int y = 54 - scroll;
            List<Module> mods = client.byCategory(c);
            for (Module m : mods) {
                if (mouseX >= x && mouseX <= x + 150 && mouseY >= y && mouseY <= y + 14) {
                    m.toggle();
                    return;
                }
                y += 16;
                if (m.isEnabled()) {
                    for (Setting s : m.getSettings()) {
                        if (mouseX >= x + 6 && mouseX <= x + 146 && mouseY >= y && mouseY <= y + 12) {
                            if (s instanceof ModeSetting) {
                                ((ModeSetting)s).next();
                            } else if (s instanceof SliderSetting) {
                                SliderSetting sl = (SliderSetting)s;
                                double pct = (mouseX - (x + 6)) / 140.0;
                                sl.setValue(sl.getMin() + (sl.getMax() - sl.getMin()) * pct);
                            }
                            return;
                        }
                        y += 13;
                    }
                }
            }
            x += 170;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0) scroll += d > 0 ? -10 : 10;
        if (scroll < 0) scroll = 0;
    }
}
