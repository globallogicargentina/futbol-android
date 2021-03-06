package com.globallogic.futbol.strategies.ion.example.operations;

import android.content.Intent;

import com.globallogic.futbol.core.broadcasts.OperationHttpBroadcastReceiver;
import com.globallogic.futbol.core.interfaces.callbacks.IStrategyHttpCallback;
import com.globallogic.futbol.core.operations.OperationHelper;
import com.globallogic.futbol.core.responses.StrategyHttpResponse;
import com.globallogic.futbol.core.strategies.OperationStrategy;
import com.globallogic.futbol.core.strategies.mock.StrategyHttpMock;
import com.globallogic.futbol.strategies.ion.StrategyIonSingleStringPost;
import com.globallogic.futbol.strategies.ion.example.BuildConfig;
import com.globallogic.futbol.strategies.ion.example.entities.Device;
import com.globallogic.futbol.strategies.ion.example.operations.helper.ExampleOperation;
import com.google.gson.JsonObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;

/**
 * Created by Facundo Mengoni on 8/6/2015.
 * GlobalLogic | facundo.mengoni@globallogic.com
 */
public class CreateDeviceOperation extends ExampleOperation {
    private static final String TAG = CreateDeviceOperation.class.getSimpleName();

    private Boolean mock = false || BuildConfig.MOCK;

    private String mUrl = "http://172.17.201.125:1337/device/";

    public void execute(String name, String resolution) {
        performOperation(name, resolution);
    }

    @Override
    protected ArrayList<OperationStrategy> getStrategies(Object... arg) {
        String name = (String) arg[0];
        String resolution = (String) arg[1];

        ArrayList<OperationStrategy> strategies = new ArrayList<>();
        BaseHttpAnalyzer analyzer = new BaseHttpAnalyzer() {
            private Device mDevice;

            @Override
            public Boolean analyzeResult(Integer aHttpCode, String aString) {
                switch (aHttpCode) {
                    case HttpURLConnection.HTTP_CREATED:
                        this.mDevice = OperationHelper.getModelObject(aString, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Device.class);
                        return true;
                }
                return false;
            }

            @Override
            public void addExtrasForResultOk(Intent intent) {
                intent.putExtra(CreateDeviceReceiver.EXTRA_DEVICE, mDevice);
            }
        };

        if (mock) {
            StrategyHttpMock strategyMock = new StrategyHttpMock(this, analyzer, 0f);
            strategyMock.add(new StrategyHttpResponse(HttpURLConnection.HTTP_CREATED, "{\"createdAt\":\"2015-08-05T11:14:45.374Z\",\"id\":\"1\",\"name\":\"S3\",\"resolution\":\"720x1280\",\"updatedAt\":\"2015-08-05T11:14:45.374Z\"}"));
            strategyMock.add(new StrategyHttpResponse(HttpURLConnection.HTTP_CREATED, "{\"createdAt\":\"2015-08-05T11:14:45.374Z\",\"id\":\"2\",\"name\":\"" + name + "\",\"resolution\":\"" + resolution + "\",\"updatedAt\":\"2015-08-05T11:14:45.374Z\"}"));
            strategies.add(strategyMock);
        } else {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("resolution", resolution);
            StrategyIonSingleStringPost strategy = new StrategyIonSingleStringPost(this, analyzer, mUrl, json.toString());
            strategies.add(strategy);
        }
        return strategies;
    }

    public interface ICreateDeviceReceiver extends IStrategyHttpCallback {
        void onSuccess(Device aDevice);

        void onError();
    }

    public static class CreateDeviceReceiver extends OperationHttpBroadcastReceiver {
        static final String EXTRA_DEVICE = "EXTRA_DEVICE";
        private final ICreateDeviceReceiver mCallback;

        public CreateDeviceReceiver(ICreateDeviceReceiver callback) {
            super(callback);
            mCallback = callback;
        }

        protected void onResultOK(Intent anIntent) {
            Device device = (Device) anIntent.getSerializableExtra(EXTRA_DEVICE);
            if (device != null)
                mCallback.onSuccess(device);
            else
                mCallback.onError();
        }

        protected void onResultError(Intent anIntent) {
            mCallback.onError();
        }
    }
}