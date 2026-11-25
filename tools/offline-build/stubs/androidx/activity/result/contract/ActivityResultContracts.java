package androidx.activity.result.contract;
public final class ActivityResultContracts {
    private ActivityResultContracts() {}
    public static class CreateDocument extends ActivityResultContract<String, android.net.Uri> {
        public CreateDocument(String mimeType) {}
    }
    public static class OpenDocument extends ActivityResultContract<String[], android.net.Uri> {
        public OpenDocument() {}
    }
    public static class GetContent extends ActivityResultContract<String, android.net.Uri> {
        public GetContent() {}
    }
    public static class PickVisualMedia extends ActivityResultContract<String, android.net.Uri> {
        public PickVisualMedia() {}
    }
}
