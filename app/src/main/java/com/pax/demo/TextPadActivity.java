package com.pax.demo;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.pax.dal.IPrinter;
import com.pax.neptunelite.api.NeptuneLiteUser;

public class TextPadActivity extends AppCompatActivity {

    private static final long PRINT_HOLD_DURATION_MS = 3000L;
    private EditText input;
    private Button printButton;

    private final Handler printHandler = new Handler(Looper.getMainLooper());
    private Runnable printRunnable;
    private boolean printHoldScheduled;

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

        input = (EditText) findViewById(R.id.text_input);
        printButton = (Button) findViewById(R.id.btn_print_text);

        if (input != null) {
            input.requestFocus();
            input.post(new Runnable() {
                @Override public void run() {
                    showKeyboard();
                }
            });
        }

        if (printButton != null) {
            printButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            schedulePrintHold();
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            cancelPrintHold();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            if (!isEventInsideView(v, event)) {
                                cancelPrintHold();
                            }
                            return true;
                        default:
                            return false;
                    }
                }
            });
        }
    }

    private boolean isEventInsideView(View v, MotionEvent event) {
        if (v == null || event == null) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        return x >= 0 && x <= v.getWidth() && y >= 0 && y <= v.getHeight();
    }

    private void schedulePrintHold() {
        if (printHoldScheduled) {
            return;
        }
        if (printRunnable == null) {
            printRunnable = new Runnable() {
                @Override
                public void run() {
                    printHoldScheduled = false;
                    executePrint();
                }
            };
        }
        printHoldScheduled = true;
        printHandler.postDelayed(printRunnable, PRINT_HOLD_DURATION_MS);
        Toast.makeText(this, R.string.text_pad_print_hold_hint, Toast.LENGTH_SHORT).show();
    }

    private void cancelPrintHold() {
        if (!printHoldScheduled) {
            return;
        }
        if (printRunnable != null) {
            printHandler.removeCallbacks(printRunnable);
        }
        printHoldScheduled = false;
    }

    private void executePrint() {
        if (input == null) {
            return;
        }
        CharSequence current = input.getText();
        final String printable = current == null ? "" : current.toString().trim();
        if (printable.length() == 0) {
            Toast.makeText(this, R.string.text_pad_print_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                String errorMessage = null;
                try {
                    IPrinter printer = NeptuneLiteUser.getInstance()
                            .getDal(getApplicationContext())
                            .getPrinter();
                    printer.init();
                    String header = getString(R.string.reader_receipt_header);
                    StringBuilder body = new StringBuilder();
                    body.append("\n\n").append(header).append("\n\n");
                    body.append(printable).append("\n\n\n");
                    printer.printStr(body.toString(), null);
                    int ret = printer.start();
                    if (ret == 0) {
                        success = true;
                    } else {
                        errorMessage = getString(R.string.text_pad_print_status_error, ret);
                    }
                } catch (Exception e) {
                    errorMessage = e.getMessage();
                }

                final boolean printSuccess = success;
                final String finalError = errorMessage;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (printSuccess) {
                            if (input != null) {
                                input.setText("");
                            }
                            Toast.makeText(TextPadActivity.this,
                                    R.string.text_pad_print_success,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            String message = (finalError == null || finalError.trim().length() == 0)
                                    ? getString(R.string.text_pad_print_failed_generic)
                                    : finalError;
                            Toast.makeText(TextPadActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
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

    @Override
    protected void onDestroy() {
        cancelPrintHold();
        super.onDestroy();
    }
}
