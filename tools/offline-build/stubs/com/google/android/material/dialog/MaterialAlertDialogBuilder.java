package com.google.android.material.dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
public class MaterialAlertDialogBuilder {
    public MaterialAlertDialogBuilder(Context context) {}
    public MaterialAlertDialogBuilder setTitle(CharSequence title) { return this; }
    public MaterialAlertDialogBuilder setMessage(CharSequence message) { return this; }
    public MaterialAlertDialogBuilder setView(View view) { return this; }
    public MaterialAlertDialogBuilder setPositiveButton(CharSequence text, DialogInterface.OnClickListener l) { return this; }
    public MaterialAlertDialogBuilder setNegativeButton(CharSequence text, DialogInterface.OnClickListener l) { return this; }
    public MaterialAlertDialogBuilder setNeutralButton(CharSequence text, DialogInterface.OnClickListener l) { return this; }
    public MaterialAlertDialogBuilder setItems(CharSequence[] items, DialogInterface.OnClickListener l) { return this; }
    public MaterialAlertDialogBuilder setCancelable(boolean cancelable) { return this; }
    public android.app.AlertDialog show() { return null; }
}
