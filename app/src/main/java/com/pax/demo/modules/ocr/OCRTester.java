package com.pax.demo.modules.ocr;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.pax.dal.IOCR;
import com.pax.dal.entity.OCRMRZResult;
import com.pax.dal.entity.OCRResult;
import com.pax.dal.exceptions.OCRException;
import com.pax.demo.base.DemoApp;
import com.pax.demo.util.BaseTester;

/**
 * @author JQChen.
 * @date on 2020/4/21.
 */
public class OCRTester extends BaseTester {
    public static final int WHAT_OCR = 0;
    private Handler handler;

    private IOCR iocr;

    public OCRTester(Handler handler) {
        this.handler = handler;
        iocr = DemoApp.getDal().getOCR();
        init();
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    private void init() {
        try {
            iocr.setAuthId("000004");
        } catch (OCRException e) {
            e.printStackTrace();
        }
    }

    public void ocr(int camerId) {
        try {
            iocr.open();
            // iocr.setCameraId(camerId);
            Bundle bundle = new Bundle();
            bundle.putInt("cameraId", camerId);
            bundle.putBoolean("isFlashOn", true);
            bundle.putBoolean("isAutoFocus", true);
            iocr.setPreviewParam(bundle);
            iocr.startPreview(IOCR.TYPE_MRZ, 60000, new IOCR.IOCRListener() {
                @Override
                public void onSucess(OCRResult ocrResult) {
                    OCRMRZResult ocrmrzResult = (OCRMRZResult) ocrResult;
                    Message.obtain(handler, WHAT_OCR, ocrmrzResult.toString()).sendToTarget();
                    release();
                }

                @Override
                public void onError(int i) {
                    Message.obtain(handler, WHAT_OCR, String.valueOf("Errcode = " + i)).sendToTarget();
                    release();
                }
            });
        } catch (OCRException e) {
            e.printStackTrace();
            release();
        }
    }

    private void release() {
        try {
            iocr.stopPreview();
            iocr.close();
        } catch (OCRException e) {
            e.printStackTrace();
        }
    }
}
