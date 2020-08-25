package com.example.demo.joanacore.joana;

import com.ibm.wala.util.MonitorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SliceMonitor implements MonitorUtil.IProgressMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SliceMonitor.class);
    private boolean isCancel=false;
    @Override
    public void beginTask(String task, int totalWork) {
        LOGGER.info("Begin task: "+ task+" ,total task: "+ totalWork);
    }

    @Override
    public void subTask(String subTask) {
        LOGGER.debug("Sub task: "+ subTask);
    }

    @Override
    public void cancel() {
        LOGGER.info("cancel");
        isCancel=true;
    }

    @Override
    public boolean isCanceled() {
        return isCancel;
    }

    @Override
    public void done() {
        LOGGER.debug("done.");
    }

    @Override
    public void worked(int units) {
        LOGGER.debug("worked: "+units);
    }

    @Override
    public String getCancelMessage() {
        return null;
    }
}
