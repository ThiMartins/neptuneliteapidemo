package com.pax.demo;

import android.content.Intent;
import android.media.AudioTrack;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.pax.dal.IPrinter;
import com.pax.dal.entity.EPiccRemoveMode;
import com.pax.dal.entity.EPiccType;
import com.pax.dal.entity.TrackData;

import com.pax.demo.base.DALTestActivity;
import com.pax.demo.modules.icc.IccTester;
import com.pax.demo.modules.mag.MagTester;
import com.pax.demo.modules.picc.PiccTester;

import com.pax.demo.util.Convert;
import com.pax.demo.util.FloatView;
import com.pax.demo.util.IApdu;
import com.pax.demo.util.IApdu.IApduReq;
import com.pax.demo.util.IApdu.IApduResp;
import com.pax.demo.util.Packer;
import com.pax.neptunelite.api.NeptuneLiteUser;

public class ReaderActivity extends AppCompatActivity {

    // UI
    private TextView tvResult;
    private Button btnStart, btnDal, btnClear, btnCalc, btnText, btnCamera;
    private CheckBox cbBeep, cbPrint;
    private ScrollView scrollView;

    // Toggles
    private volatile boolean beepEnabled = true;
    private volatile boolean printEnabled = false;

    // Infra
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FloatView floatView = null;

    // PICC flags
    private final StringBuilder piccLogBuffer = new StringBuilder();
    private volatile boolean piccHasDataOut = false;
    private volatile boolean piccFirstLineSeen = false;
    private volatile boolean piccPrintedThisCycle = false;
    private static final EPiccType PICC_TYPE = EPiccType.INTERNAL;

    private static final boolean CAMERA_SHORTCUT_ENABLED = false;

    // Cores do botão principal (ARGB)
    private static final int COLOR_OPEN  = 0xFF43A047; // verde Abrir Loja
    private static final int COLOR_CLOSE = 0xFFE53935; // vermelho Fechar Loja

    // Handler de logs do PICC (beep + impressão imediata)
    private final Handler piccHandler = new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(android.os.Message msg) {
            if (msg == null || msg.obj == null) return;
            String line = String.valueOf(msg.obj);

            // Beep e impressão imediatos na primeira saída do ciclo
            if (!piccFirstLineSeen) {
                piccFirstLineSeen = true;
                if (beepEnabled) {
                    beep();
                    beep();
                }
                if (printEnabled && !piccPrintedThisCycle) {
                    piccPrintedThisCycle = true;
                    printSimple(line == null || line.length() == 0 ? "PICC: leitura iniciada." : line);
                }
            }

            appendLine(line);

            synchronized (piccLogBuffer) {
                if (piccLogBuffer.length() > 0) piccLogBuffer.append('\n');
                piccLogBuffer.append(line);
            }

            String low = line.toLowerCase();
            if (low.contains("dataout") || low.contains("uid") || low.contains("ats")
                    || low.contains("sak") || low.contains("ndef")
                    || low.contains("apdu") || low.contains("resp")
                    || low.contains("success") || low.contains("ok")) {
                piccHasDataOut = true;
            }
        }
    };

    // Threads e estado
    private volatile boolean runningMag, runningIcc, runningPicc;
    private Thread thMag, thIcc, thPicc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader_base);

        floatView = FloatView.getInstance(this);
        try { floatView.createFloatView(20, 20); } catch (Throwable ignored) {}

        try { Convert.getInstance().setContext(getApplicationContext()); } catch (Throwable ignored) {}
        try { Packer.getInstance().setContext(getApplicationContext()); } catch (Throwable ignored) {}

        tvResult   = (TextView) findViewById(R.id.tv_result);
        btnStart   = (Button) findViewById(R.id.btn_start);
        btnDal     = (Button) findViewById(R.id.btn_open_dal);
        btnClear   = (Button) findViewById(R.id.btn_clear);
        btnCalc    = (Button) findViewById(R.id.btn_calc);
        btnText    = (Button) findViewById(R.id.btn_text);
        btnCamera  = (Button) findViewById(R.id.btn_camera);
        cbBeep     = (CheckBox) findViewById(R.id.cb_beep);
        cbPrint    = (CheckBox) findViewById(R.id.cb_print);
        scrollView = (ScrollView) findViewById(R.id.scroll_container);

        tvResult.setMovementMethod(ScrollingMovementMethod.getInstance());
        tvResult.setVerticalScrollBarEnabled(true);

        if (cbBeep != null) cbBeep.setChecked(true);
        if (cbPrint != null) cbPrint.setChecked(false);

        if (cbBeep != null) cbBeep.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                beepEnabled = isChecked;
            }
        });
        if (cbPrint != null) cbPrint.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                printEnabled = isChecked;
            }
        });

        if (btnClear != null) btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { clearLog(); }
        });

        if (btnStart != null) btnStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (isAnyRunning()) stopAll(); else startAll(); }
        });

        if (btnDal != null) btnDal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { safeOpenDal(); }
        });

        // === NEW: botão Calculadora (adição mínima, sem tocar nas leituras) ===
        if (btnCalc != null) {
            btnCalc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    openCalculator();
                }
            });
        }

        if (btnText != null) {
            btnText.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    openTextPad();
                }
            });
        }

        if (btnCamera != null) {
            if (CAMERA_SHORTCUT_ENABLED) {
                btnCamera.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        openCamera();
                    }
                });
            } else {
                btnCamera.setEnabled(false);
            }
        }

        clearLog();
        appendLine("Iniciando leituras MAG/ICC/PICC...");
        startAll();
    }

    @Override protected void onPause() { super.onPause(); stopAll(); }
    @Override protected void onStop()  { super.onStop();  stopAll(); }
    @Override protected void onDestroy() {
        stopAll();
        try { mainHandler.removeCallbacksAndMessages(null); } catch (Throwable ignored) {}
        try { piccHandler.removeCallbacksAndMessages(null); } catch (Throwable ignored) {}
        try { if (floatView != null) floatView.removeFloatView(); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    /* =========================== Master =========================== */
    private boolean isAnyRunning() { return runningMag || runningIcc || runningPicc; }
    private void startAll() { startMag(); startIcc(); startPicc(); applyButtonsState(); }
    private void stopAll()  { stopMag(); stopIcc(); stopPicc(); applyButtonsState(); }

    private void safeOpenDal() {
        stopAll();
        startActivity(new Intent(ReaderActivity.this, DALTestActivity.class));
    }

    // =========================== Botão principal (texto + cor) ===========================
    private void applyButtonsState() {
        if (btnStart != null) {
            boolean aberto = isAnyRunning();
            btnStart.setText(aberto ? R.string.reader_start_close : R.string.reader_start_open);
            // cor do fundo: verde quando fechado (para abrir), vermelho quando aberto (para fechar)
            try {
                btnStart.setBackgroundColor(aberto ? COLOR_CLOSE : COLOR_OPEN);
            } catch (Throwable ignored) {} // defensivo para temas que sobrepõem background
        }
    }

    /* =========================== MAG =========================== */
    private void startMag() {
        if (runningMag) return;
        try {
            MagTester.getInstance().open();
            MagTester.getInstance().reset();
        } catch (Exception e) { appendLine("MAG: erro ao abrir - " + e.getMessage()); return; }
        runningMag = true; applyButtonsState();
        appendLine("MAG pronto. Passe o cartão.");
        thMag = new Thread(new Runnable() {
            @Override public void run() {
                while (runningMag) {
                    try {
                        if (MagTester.getInstance().isSwiped()) {
                            TrackData data = MagTester.getInstance().read();
                            String result = buildMagResult(data);
                            appendLine(result);
                            if (beepEnabled) {
                                beep();
                                beep();
                            }
                            if (printEnabled) printSimple(result);
                            MagTester.getInstance().reset();
                        }
                        Thread.sleep(100);
                    } catch (Exception ex) {
                        appendLine("MAG erro: " + ex.getMessage());
                        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    }
                }
                try { MagTester.getInstance().close(); } catch (Exception ignored) {}
            }
        }, "MAG");
        thMag.start();
    }

    private void stopMag() {
        runningMag = false;
        if (thMag != null) { try { thMag.interrupt(); } catch (Throwable ignored) {} thMag = null; }
        try { MagTester.getInstance().close(); } catch (Throwable ignored) {}
        applyButtonsState();
        appendLine("MAG: parado.");
    }

    private String buildMagResult(TrackData t) {
        if (t == null) return "MAG: nenhum dado lido";
        StringBuilder sb = new StringBuilder();
        sb.append("MAG ResultCode: ").append(t.getResultCode());
        if ((t.getResultCode() & 1) != 0) sb.append("\nTrack1: ").append(t.getTrack1());
        if ((t.getResultCode() & 2) != 0) sb.append("\nTrack2: ").append(t.getTrack2());
        if ((t.getResultCode() & 4) != 0) sb.append("\nTrack3: ").append(t.getTrack3());
        return sb.toString();
    }

    /* =========================== ICC =========================== */
    private void startIcc() {
        if (runningIcc) return;
        runningIcc = true;
        applyButtonsState();
        appendLine("ICC: insira o cartão com chip.");

        thIcc = new Thread(new Runnable() {
            @Override public void run() {
                boolean sessionActive = false;

                while (runningIcc) {
                    try {
                        boolean present = IccTester.getInstance().detect((byte)0);

                        if (present && !sessionActive) {
                            sessionActive = true;

                            String res = "ICC detectado.";
                            try {
                                byte[] atr = IccTester.getInstance().init((byte)0);
                                if (atr != null)
                                    res += "\nATR: " + Convert.getInstance().bcdToStr(atr);
                                else res += "\nATR: <nulo>";
                            } catch (Exception e) {
                                res += "\nFalha ao inicializar ICC: " + e.getMessage();
                            }

                            try {
                                IApdu apdu = Packer.getInstance().getApdu();
                                IApduReq req = apdu.createReq(
                                        (byte)0x00,(byte)0xA4,(byte)0x04,(byte)0x00,
                                        "1PAY.SYS.DDF01".getBytes(),(byte)0x00);
                                byte[] out = req.pack();
                                byte[] resp = IccTester.getInstance().isoCommand((byte)0, out);
                                if (resp != null) {
                                    IApduResp r = apdu.unpack(resp);
                                    res += "\nISO resp: Status=" + r.getStatus() + " " + r.getStatusString();
                                }
                            } catch (Exception ignore) {}

                            appendLine(res);
                            if (beepEnabled) {
                                beep();
                                beep();
                            }
                            if (printEnabled) printSimple(res);

                            try { IccTester.getInstance().close((byte)0); } catch (Exception ignored) {}

                            waitForIccRemoval();
                            sessionActive = false;
                            appendLine("ICC: cartão removido. Pronto para nova inserção.");
                        }

                        Thread.sleep(200);

                    } catch (Exception ex) {
                        appendLine("ICC erro: " + ex.getMessage());
                        try { Thread.sleep(400); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
                    }
                }

                try { IccTester.getInstance().close((byte)0); } catch (Exception ignored) {}
            }
        }, "ICC");
        thIcc.start();
    }

    private void waitForIccRemoval() {
        try {
            while (runningIcc) {
                boolean stillPresent;
                try { stillPresent = IccTester.getInstance().detect((byte)0); }
                catch (Exception e) { stillPresent = true; }
                if (!stillPresent) break;
                Thread.sleep(250);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void stopIcc() {
        runningIcc = false;
        if (thIcc != null) { try { thIcc.interrupt(); } catch (Throwable ignored) {} thIcc = null; }
        try { IccTester.getInstance().close((byte)0); } catch (Throwable ignored) {}
        applyButtonsState();
        appendLine("ICC: parado.");
    }

    /* =========================== PICC =========================== */
    private void startPicc() {
        if (runningPicc) return;
        synchronized (piccLogBuffer) { piccLogBuffer.setLength(0); }
        piccHasDataOut = false;
        piccFirstLineSeen = false;
        piccPrintedThisCycle = false;

        try { PiccTester.getInstance(PICC_TYPE).open(); } catch (Exception e) {
            appendLine("PICC: erro ao abrir - " + e.getMessage());
            return;
        }
        runningPicc = true; applyButtonsState();
        appendLine("PICC: aproxime o cartão.");
        thPicc = new Thread(new Runnable() {
            @Override public void run() {
                while (runningPicc) {
                    try {
                        PiccTester.getInstance(PICC_TYPE).detectAorBandCommand(piccHandler);

                        if (printEnabled && piccHasDataOut && !piccPrintedThisCycle) {
                            String toPrint;
                            synchronized (piccLogBuffer) {
                                toPrint = piccLogBuffer.toString().trim();
                                piccLogBuffer.setLength(0);
                            }
                            if (toPrint.length() == 0) toPrint = "Leitura PICC concluída.";
                            printSimple(toPrint);
                        }

                        try { PiccTester.getInstance(PICC_TYPE).remove(EPiccRemoveMode.REMOVE, (byte)0); } catch (Exception ignore) {}
                        piccHasDataOut = false;
                        piccFirstLineSeen = false;
                        piccPrintedThisCycle = false;

                        Thread.sleep(1000);
                    } catch (Exception ex) {
                        appendLine("PICC erro: " + ex.getMessage());
                        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    }
                }
                try { PiccTester.getInstance(PICC_TYPE).close(); } catch (Exception ignored) {}
            }
        }, "PICC");
        thPicc.start();
    }

    private void stopPicc() {
        runningPicc = false;
        if (thPicc != null) { try { thPicc.interrupt(); } catch (Throwable ignored) {} thPicc = null; }
        try { PiccTester.getInstance(PICC_TYPE).close(); } catch (Throwable ignored) {}
        synchronized (piccLogBuffer) { piccLogBuffer.setLength(0); }
        piccHasDataOut = false;
        piccFirstLineSeen = false;
        piccPrintedThisCycle = false;
        applyButtonsState();
        appendLine("PICC: parado.");
    }

    /* =========================== Beep (1s em 800Hz, agradável) =========================== */
    private void beep() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    playTone(800 /*Hz*/, 250 /*ms*/);
                } catch (Exception e) {
                    appendLine("Erro no beep: " + e.getMessage());
                }
            }
        }, "BeepSuccessThread").start();
    }

    private void playTone(int freqHz, int durationMs) throws InterruptedException {
        final int sampleRate = 16000;
        final int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
        final short[] buffer = new short[numSamples];

        final double twoPiF = 2 * Math.PI * freqHz;
        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            buffer[i] = (short) (Math.sin(twoPiF * t) * Short.MAX_VALUE);
        }

        AudioTrack track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.length * 2,
                AudioTrack.MODE_STREAM
        );

        try {
            track.play();
            track.write(buffer, 0, buffer.length);
            Thread.sleep(durationMs + 50);
        } finally {
            try { track.stop(); } catch (Throwable ignore) {}
            try { track.release(); } catch (Throwable ignore) {}
        }
    }

    /* =========================== Impressão =========================== */
    private void printSimple(final String text) {
        try {
            IPrinter printer = NeptuneLiteUser.getInstance().getDal(getApplicationContext()).getPrinter();
            printer.init();
            String header = ReaderActivity.this.getString(R.string.reader_receipt_header);
            printer.printStr("\n\n" + header + "\n\n" + text + "\n\n\n", null);
            int ret = printer.start();
            if (ret != 0) appendLine("Erro na impressão. Código: " + ret);
            else appendLine("Impressão concluída com sucesso.");
        } catch (Exception e) {
            appendLine("Falha ao imprimir: " + e.getMessage());
        }
    }

    /* =========================== UI helpers =========================== */
    private void appendLine(final String line) {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                CharSequence prev = tvResult.getText();
                tvResult.setText((prev == null || prev.length() == 0) ? line : prev + "\n" + line);
                if (scrollView != null) {
                    scrollView.post(new Runnable() {
                        @Override public void run() { scrollView.fullScroll(View.FOCUS_DOWN); }
                    });
                }
            }
        });
    }

    private void clearLog() {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                tvResult.setText("");
                if (scrollView != null) {
                    scrollView.post(new Runnable() {
                        @Override public void run() { scrollView.fullScroll(View.FOCUS_UP); }
                    });
                }
            }
        });
    }

    /* =========================== Calculadora =========================== */
    private void openCalculator() {
        // 1) Tentativa padrão via categoria do sistema
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_APP_CALCULATOR);
            startActivity(i);
            return;
        } catch (Throwable ignored) {}

        // 2) Fallbacks por pacotes comuns
        String[] pkgs = {
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.sec.android.app.popupcalculator",
                "com.miui.calculator",
                "com.coloros.calculator",
                "com.oneplus.calculator",
                "com.vivo.calculator",
                "com.huawei.calculator"
        };
        for (String pkg : pkgs) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) { startActivity(i); return; }
            } catch (Throwable ignored) {}
        }

        // 3) Feedback leve no log
        appendLine("Não foi possível abrir a calculadora neste dispositivo.");
    }

    private void openTextPad() {
        try {
            Intent intent = new Intent(ReaderActivity.this, TextPadActivity.class);
            startActivity(intent);
        } catch (Throwable t) {
            appendLine("Não foi possível abrir o bloco de texto.");
        }
    }

    private void openCamera() {
        try {
            Intent intent = new Intent(ReaderActivity.this, CameraActivity.class);
            intent.putExtra(CameraActivity.EXTRA_DIRECT_FRONT_SCAN, true);
            startActivity(intent);
        } catch (Throwable t) {
            appendLine("Não foi possível abrir a câmera.");
        }
    }
}
