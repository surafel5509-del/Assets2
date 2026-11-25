package androidx.activity;
public abstract class OnBackPressedCallback {
    private boolean enabled;
    public OnBackPressedCallback(boolean enabled) { this.enabled = enabled; }
    public abstract void handleOnBackPressed();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void remove() {}
}
