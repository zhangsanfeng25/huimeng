package com.huimeng.bookshop.ui;

import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.android.material.textfield.TextInputLayout;

final class AuthForm {
    private AuthForm() {}

    static void error(EditText field, String message) {
        field.requestFocus();
        mark(field, message);
    }

    static void mark(EditText field, String message) {
        TextInputLayout box = (TextInputLayout) field.getParent().getParent();
        if (box.getError() == null) {
            field.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                    box.setError(null);
                    field.removeTextChangedListener(this);
                }
                @Override public void afterTextChanged(Editable text) {}
            });
        }
        box.setErrorIconDrawable(null);
        box.setError(message);
    }

    static void missing(EditText field, String message) {
        CharSequence original = field.getHint();
        if (message.contentEquals(original)) return;
        field.setHint(message);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                field.setHint(original);
                field.removeTextChangedListener(this);
            }
            @Override public void afterTextChanged(Editable text) {}
        });
    }
}
