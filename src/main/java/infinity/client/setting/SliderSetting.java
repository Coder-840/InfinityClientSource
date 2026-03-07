package infinity.client.setting;

public class SliderSetting extends Setting {
    private final double min;
    private final double max;
    private final double step;
    private double value;

    public SliderSetting(String name, double min, double max, double step, double value) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        setValue(value);
    }

    public double getValue() {
        return value;
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    public void setValue(double value) {
        double clamped = Math.max(min, Math.min(max, value));
        this.value = Math.round(clamped / step) * step;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
}
