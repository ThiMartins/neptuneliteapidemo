package com.pax.demo;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

public class TextPadActivity extends AppCompatActivity {

    private TextView preview;
    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_pad);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.text_pad_title);
        }

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        preview = (TextView) findViewById(R.id.text_preview);
        input = (EditText) findViewById(R.id.text_input);

        if (input != null) {
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updatePreview(s);
                }
                @Override public void afterTextChanged(Editable s) { }
            });

            input.requestFocus();
            input.post(new Runnable() {
                @Override public void run() {
                    showKeyboard();
                }
            });
            updatePreview(input.getText());
        }
    }

    private void updatePreview(CharSequence text) {
        if (preview != null) {
            if (text == null || text.length() == 0) {
                preview.setText(R.string.text_pad_preview_placeholder);
            } else {
                preview.setText(text);
            }
        }
    }

    private void showKeyboard() {
        if (input == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
