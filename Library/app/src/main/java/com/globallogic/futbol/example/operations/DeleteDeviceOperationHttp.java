package com.globallogic.futbol.example.operations;

import android.content.Intent;

import com.globallogic.futbol.core.OperationApp;
import com.globallogic.futbol.core.OperationResponse;
import com.globallogic.futbol.core.interfaces.IOperationStrategy;
import com.globallogic.futbol.core.operation.OperationBroadcastReceiver;
import com.globallogic.futbol.core.operation.OperationHelper;
import com.globallogic.futbol.core.operation.strategies.StrategyHttpMock;
import com.globallogic.futbol.core.operation.strategies.StrategyHttpMockResponse;
import com.globallogic.futbol.example.entities.Device;
import com.globallogic.futbol.example.operations.helper.ExampleOperationHttp;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Created by Facundo Mengoni on 8/6/2015.
 * GlobalLogic | facundo.mengoni@globallogic.com
 */
public class DeleteDeviceOperationHttp extends ExampleOperationHttp {
    private static final String TAG = DeleteDeviceOperationHttp.class.getSimpleName();

    private Device mDevice;
    private boolean mNotFound;


    @Override
    public void reset() {
        super.reset();
        mDevice = null;
        mNotFound = false;
    }

    public void execute(String id ){
        reset();
        performOperation(id);
    }

    @Override
    protected IOperationStrategy getStrategy(Object... arg) {
        String id = (String) arg[0];

        StrategyHttpMock strategyHttpMock = new StrategyHttpMock(0f);
        try {
            strategyHttpMock.add(new StrategyHttpMockResponse(HttpURLConnection.HTTP_NOT_FOUND, ""));
            strategyHttpMock.add(new StrategyHttpMockResponse(HttpURLConnection.HTTP_OK, String.format(OperationHelper.assetsReader(OperationApp.getInstance(), "json/DeleteDeviceOperation_1.json"), id)));
        } catch (IOException e) {
        }
        return strategyHttpMock;
    }

    @Override
    public Boolean analyzeResult(OperationResponse<Integer, String> response) {
        switch (response.getResultCode()) {
            case HttpURLConnection.HTTP_NOT_FOUND:
                this.mNotFound = true;
                return true;
            case HttpURLConnection.HTTP_OK:
                this.mDevice = OperationHelper.getModelObject(response.getResult(), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Device.class);
                return true;
        }
        return false;
    }

    @Override
    protected void addExtrasForResultOk(Intent intent) {
        if (mNotFound)
            intent.putExtra(DeleteDeviceReceiver.EXTRA_NOT_FOUND, true);
        else
            intent.putExtra(DeleteDeviceReceiver.EXTRA_DEVICE, mDevice);
    }

    public interface IDeleteDeviceReceiver {
        void onNoInternet();

        void onStartOperation();

        void onSuccess(Device aDevice);

        void onError();

        void onNotFound();
    }

    public static class DeleteDeviceReceiver extends OperationBroadcastReceiver {
        static final String EXTRA_NOT_FOUND = "EXTRA_NOT_FOUND";
        static final String EXTRA_DEVICE = "EXTRA_DEVICE";
        private final IDeleteDeviceReceiver mCallback;

        public DeleteDeviceReceiver(IDeleteDeviceReceiver callback) {
            super();
            mCallback = callback;
        }

        @Override
        protected void onNoInternet() {
            mCallback.onNoInternet();
        }

        @Override
        protected void onStartOperation() {
            mCallback.onStartOperation();
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