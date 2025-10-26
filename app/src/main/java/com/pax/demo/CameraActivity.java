package com.pax.demo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.pax.dal.entity.EScannerType;
import com.pax.demo.modules.scanner.ScannerSelectFragment;
import com.pax.demo.modules.scanner.ScanFragment;

public class CameraActivity extends AppCompatActivity {

    public static final String EXTRA_DIRECT_FRONT_SCAN = "com.pax.demo.extra.DIRECT_FRONT_SCAN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.camera_title);
        }

        if (savedInstanceState == null) {
            if (getIntent().getBooleanExtra(EXTRA_DIRECT_FRONT_SCAN, false)) {
                Bundle args = new Bundle();
                args.putString("scannerType", EScannerType.REAR.name());
                args.putBoolean(ScanFragment.ARG_AUTO_FRONT, true);

                ScanFragment fragment = new ScanFragment();
                fragment.setArguments(args);

                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.parent_layout, fragment)
                        .commit();
            } else {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.parent_layout, new ScannerSelectFragment())
                        .commit();
            }
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
