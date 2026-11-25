package androidx.appcompat.app;
public class AppCompatActivity extends android.app.Activity {
    private final androidx.activity.OnBackPressedDispatcher dispatcher = new androidx.activity.OnBackPressedDispatcher();
    public androidx.activity.OnBackPressedDispatcher getOnBackPressedDispatcher() { return dispatcher; }
    public final <I, O> androidx.activity.result.ActivityResultLauncher<I> registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContract<I, O> contract,
            androidx.activity.result.ActivityResultCallback<O> callback) {
        return new androidx.activity.result.ActivityResultLauncher<I>() {};
    }
    public final <I, O> androidx.activity.result.ActivityResultLauncher<I> registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContract<I, O> contract) {
        return new androidx.activity.result.ActivityResultLauncher<I>() {};
    }
}
