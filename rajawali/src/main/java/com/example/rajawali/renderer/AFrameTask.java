package com.example.rajawali.renderer;

import com.example.rajawali.util.RajLog;

/**
 * @author Jared Woolston (jwoolston@tenkiv.com)
 */
public abstract class AFrameTask implements Runnable {

    protected abstract void doTask();

    @Override
    public void run() {
        try {
            doTask();
        } catch (Exception e) {
            RajLog.e("Execution Failed: " + e.getMessage());
        }
    }
}
