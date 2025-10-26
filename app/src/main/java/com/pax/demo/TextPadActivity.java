package com.pax.demo;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * Tela simples para digitação com letras grandes.
 */
public class TextPadActivity extends AppCompatActivity {

    private EditText editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_pad);
        setTitle(R.string.reader_text_title);

        editor = (EditText) findViewById(R.id.text_pad_editor);
        if (editor != null) {
            editor.requestFocus();
            editor.post(new Runnable() {
                @Override
                public void run() {
                    showKeyboard();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        showKeyboard();
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideKeyboard();
    }

    private void showKeyboard() {
        if (editor == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private void hideKeyboard() {
        if (editor == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        }
    }
}
