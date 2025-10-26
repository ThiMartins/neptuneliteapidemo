package com.pax.demo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.pax.dal.entity.EScannerType;
import com.pax.demo.modules.scanner.ScannerSelectFragment;
import com.pax.demo.modules.scanner.ScanFragment;

public class CameraActivity extends AppCompatActivity {

    public static final String EXTRA_DIRECT_FRONT_SCAN = "com.pax.demo.extra.DIRECT_FRONT_SCAN";

    private static final String STATE_STARTED_SHORTCUT = "stateStartedShortcut";
    private static final String STATE_SHOWING_DIRECT = "stateShowingDirect";

    private boolean startedFromShortcut;
    private boolean showingDirect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.camera_title);
        }

        if (savedInstanceState != null) {
            startedFromShortcut = savedInstanceState.getBoolean(STATE_STARTED_SHORTCUT, false);
            showingDirect = savedInstanceState.getBoolean(STATE_SHOWING_DIRECT, false);
        } else {
            startedFromShortcut = getIntent().getBooleanExtra(EXTRA_DIRECT_FRONT_SCAN, false);
            if (startedFromShortcut) {
                showDirectScan();
            } else {
                showScannerSelect();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_STARTED_SHORTCUT, startedFromShortcut);
        outState.putBoolean(STATE_SHOWING_DIRECT, showingDirect);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (!returnToMenuIfNeeded()) {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (!returnToMenuIfNeeded()) {
            super.onBackPressed();
        }
    }

    private boolean returnToMenuIfNeeded() {
        if (startedFromShortcut && showingDirect) {
            showScannerSelect();
            return true;
        }
        return false;
    }

    private void showDirectScan() {
        Bundle args = new Bundle();
        args.putString("scannerType", EScannerType.REAR.name());
        args.putBoolean(ScanFragment.ARG_AUTO_FRONT, true);

        ScanFragment fragment = new ScanFragment();
        fragment.setArguments(args);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.parent_layout, fragment)
                .commit();

        startedFromShortcut = true;
        showingDirect = true;
    }

    private void showScannerSelect() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.parent_layout, new ScannerSelectFragment())
                .commit();

        startedFromShortcut = false;
        showingDirect = false;
    }
}
