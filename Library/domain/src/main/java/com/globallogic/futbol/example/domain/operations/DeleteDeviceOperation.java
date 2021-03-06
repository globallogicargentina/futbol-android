package com.globallogic.futbol.example.domain.operations;

import android.content.Intent;

import com.globallogic.futbol.core.OperationApp;
import com.globallogic.futbol.core.broadcasts.OperationHttpBroadcastReceiver;
import com.globallogic.futbol.core.interfaces.callbacks.IStrategyHttpCallback;
import com.globallogic.futbol.core.operations.OperationHelper;
import com.globallogic.futbol.core.responses.StrategyHttpResponse;
import com.globallogic.futbol.core.strategies.OperationStrategy;
import com.globallogic.futbol.core.strategies.mock.StrategyHttpMock;
import com.globallogic.futbol.example.domain.models.Device;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by Facundo Mengoni on 8/6/2015.
 * GlobalLogic | facundo.mengoni@globallogic.com
 */
public class DeleteDeviceOperation extends BaseOperation {
    private static final String TAG = DeleteDeviceOperation.class.getSimpleName();

    public void execute(String id) {
        performOperation(id);
    }

    @Override
    protected ArrayList<OperationStrategy> getStrategies(Object... arg) {
        String id = (String) arg[0];

        StrategyHttpMock strategyHttpMock = new StrategyHttpMock(this, new BaseHttpAnalyzer() {
            private Device mDevice;
            private boolean mNotFound;

            @Override
            public Boolean analyzeResult(Integer aHttpCode, String aString) {
                switch (aHttpCode) {
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        this.mNotFound = true;
                        return true;
                    case HttpURLConnection.HTTP_OK:
                        this.mDevice = OperationHelper.getModelObject(aString, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Device.class);
                        return true;
                }
                return false;
            }

            @Override
            public void reset() {
                mDevice = null;
                mNotFound = false;
            }

            @Override
            public void addExtrasForResultOk(Intent intent) {
                if (mNotFound)
                    intent.putExtra(DeleteDeviceReceiver.EXTRA_NOT_FOUND, true);
                else
                    intent.putExtra(DeleteDeviceReceiver.EXTRA_DEVICE, mDevice);
            }
        }, 0f);
        try {
            strategyHttpMock.add(new StrategyHttpResponse(HttpURLConnection.HTTP_NOT_FOUND, ""));
            strategyHttpMock.add(new StrategyHttpResponse(HttpURLConnection.HTTP_OK, String.format(OperationHelper.assetsReader(OperationApp.getInstance(), "json/DeleteDeviceOperation_1.json"), id)));
        } catch (IOException ignored) {
        }
        return new ArrayList<OperationStrategy>(Collections.singletonList(strategyHttpMock));
    }

    public interface IDeleteDeviceReceiver extends IStrategyHttpCallback {
        void onSuccess(Device aDevice);

        void onError();

        void onNotFound();
    }

    public static class DeleteDeviceReceiver extends OperationHttpBroadcastReceiver {
        static final String EXTRA_NOT_FOUND = "EXTRA_NOT_FOUND";
        static final String EXTRA_DEVICE = "EXTRA_DEVICE";
        private final IDeleteDeviceReceiver mCallback;

        public DeleteDeviceReceiver(IDeleteDeviceReceiver callback) {
            super(callback);
            mCallback = callback;
        }

        protected void onResultOK(Intent anIntent) {
            boolean notFoundFound = anIntent.getBooleanExtra(EXTRA_NOT_FOUND, false);
            if (!notFoundFound) {
                Device device = (Device) anIntent.getSerializableExtra(EXTRA_DEVICE);
                if (device != null)
                    mCallback.onSuccess(device);
                else
                    mCallback.onError();
            } else
                mCallback.onNotFound();
        }

        protected void onResultError(Intent anIntent) {
            mCallback.onError();
        }
    }
}