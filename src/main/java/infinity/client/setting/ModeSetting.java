package infinity.client.setting;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting {
    private final List<String> modes;
    private int index;

    public ModeSetting(String name, String defaultMode, String... modes) {
        super(name);
        this.modes = Arrays.asList(modes);
        this.index = Math.max(0, this.modes.indexOf(defaultMode));
    }

    public String getMode() {
        return modes.get(index);
    }

    public void next() {
        index = (index + 1) % modes.size();
    }

    public List<String> getModes() {
        return modes;
    }
}
