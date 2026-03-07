package infinity.client.module;

import infinity.client.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;

public abstract class Module {
    protected final Minecraft mc = Minecraft.getMinecraft();
    private final String name;
    private final Category category;
    private boolean enabled;
    private final List<Setting> settings = new ArrayList<>();

    protected Module(String name, Category category) {
        this.name = name;
        this.category = category;
    }

    protected <T extends Setting> T addSetting(T setting) {
        settings.add(setting);
        return setting;
    }

    public void toggle() {
        enabled = !enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public void onRender3D(float partialTicks) {}
    public boolean onPacketSend(Packet<?> packet) { return false; }
    public boolean onPacketReceive(Packet<?> packet) { return false; }

    public String getName() { return name; }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public List<Setting> getSettings() { return settings; }
}
