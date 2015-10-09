package com.globallogic.futbol.core.operation;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;

import com.globallogic.futbol.core.LocalBroadcastManager;
import com.globallogic.futbol.core.OperationApp;
import com.globallogic.futbol.core.OperationResponse;
import com.globallogic.futbol.core.exceptions.UnexpectedResponseException;
import com.globallogic.futbol.core.interfaces.IOperation;
import com.globallogic.futbol.core.interfaces.IOperationStrategy;
import com.globallogic.futbol.core.operation.strategies.StrategyHttpMockResponse;

import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class OperationHttp extends Operation<String> {
    //region Constants
    private static final String TAG = OperationHttp.class.getSimpleName();
    private static final String SAVE_INSTANCE_TIME_INIT = "SAVE_INSTANCE_TIME_INIT";
    private static final String SAVE_INSTANCE_ID = "SAVE_INSTANCE_ID";
    private static final String SAVE_INSTANCE_RESULT = "SAVE_INSTANCE_RESULT";
    private static final String SAVE_INSTANCE_STATE = "SAVE_INSTANCE_STATE";
    private static final String SAVE_INSTANCE_STRATEGY = "SAVE_INSTANCE_STRATEGY";
    //endregion

    //region Log
    public static Logger mLogger;

    static {
        mLogger = Logger.getLogger(TAG);
        mLogger.setLevel(Level.OFF);
    }
    //endregion

    //region Variables
    public Long mConnectionDelay = 0l;
    private Long timeInit;
    private String id;

    private OperationStatus mOperationStatus;
    private IOperationStrategy mStrategy;
    private Boolean mResult;
    //endregion

    //region Constructors

    /**
     * Create a new instance with an id empty.
     * <p/>
     * The id is used to register the receiver for a specific operation.
     * If you register two operation with different ids then the receiver
     * of one operation never listen the other operation.
     */
    protected OperationHttp() {
        this("");
    }

    /**
     * Create a new instance with the specified id.
     * <p/>
     * The id is used to register the receiver for a specific operation.
     * If you register two operation with different ids then the receiver
     * of one operation never listen the other operation.
     */
    protected OperationHttp(String id) {
        mLogger.info(String.format("Constructor with id: %s", id));
        this.id = id;
        reset();
    }
    //endregion

    //region Methods for test

    /**
     * Run the operation synchronously and return the response expected in the callback of the broadcast
     *
     * @see OperationHttp#beforeWorkInBackground()
     * @see OperationHttp#workInBackground(Exception, int, String)
     * @see OperationHttp#afterWorkInBackground(Boolean)
     */
    public Boolean testResponse(StrategyHttpMockResponse aMockResponse) {
        mLogger.info(String.format("Test response: %s", aMockResponse.toString()));
        switch (mOperationStatus) {
            default:
            case UNKNOWN:
            case READY_TO_EXECUTE:
            case WAITING_EXECUTION:
                if (!hasInternet()) {
                    sendBroadcastForNoInternet();
                    return false;
                }
                beforeWorkInBackground();
                Boolean result = workInBackground(null, aMockResponse.getHttpCode(), aMockResponse.getResponse());
                afterWorkInBackground(result);
                return true;
            case FINISHED_EXECUTION:
                afterWorkInBackgroundBroadcasts(mResult);
                return false;
            case DOING_EXECUTION:
                beforeWorkInBackgroundBroadcasts();
                return false;
        }
    }

    /**
     * Run the operation synchronously and return the exception expected in the callback of the broadcast
     *
     * @see OperationHttp#beforeWorkInBackground()
     * @see OperationHttp#workInBackground(Exception, int, String)
     * @see OperationHttp#afterWorkInBackground(Boolean)
     */
    public Boolean testResponse(Exception anException) {
        mLogger.info(String.format("Test response: %s", anException.getClass().getName()));
        switch (mOperationStatus) {
            default:
            case UNKNOWN:
            case READY_TO_EXECUTE:
            case WAITING_EXECUTION:
                if (!hasInternet()) {
                    sendBroadcastForNoInternet();
                    return false;
                }
                beforeWorkInBackground();
                Boolean result = workInBackground(anException, 0, null);
                afterWorkInBackground(result);
                return true;
            case FINISHED_EXECUTION:
                afterWorkInBackgroundBroadcasts(mResult);
                return false;
            case DOING_EXECUTION:
                beforeWorkInBackgroundBroadcasts();
                return false;
        }
    }
    //endregion

    //region IStrategyCallback

    /**
     * Analysis of the response returned by the server
     *
     * @param aException the exception thrown because of some error
     * @param OperationResponse<String>  the http response
     * @see OperationHttp#workInBackground(Exception, int, String)
     * @see OperationHttp#afterWorkInBackground(Boolean)
     */

    @Override
    public void parseResponse(final Exception aException, final OperationResponse<String> aResponse) {
        if (aException != null)
            mLogger.log(Level.SEVERE, String.format("Parsing response: %s", aException.getMessage()), aException);
        if (TextUtils.isEmpty(aResponse.getResult()) || !(aResponse.getResult().startsWith("{") || aResponse.getResult().startsWith("[")))
            mLogger.severe(String.format("Parsing response: %s", aResponse.getResult()));
        else
            mLogger.info(String.format("Parsing response: %s", aResponse.getResult()));

        // Parse and analyze
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return workInBackground(aException, aResponse.getResultCode(), aResponse.getResult());
            }

            @Override
            protected void onPostExecute(Boolean result) {
                super.onPostExecute(result);
                afterWorkInBackground(result);
            }
        }.execute((Void) null);
    }
    //endregion

    //region IOperation

    /**
     * It registers that the operation would be executed but for some reason
     * could not be implemented and that there is someone waiting to do.
     */
    @Override
    public IOperation setAsWaiting() {
        mLogger.info("Setting as waiting");
        mOperationStatus = OperationStatus.WAITING_EXECUTION;
        return this;
    }

    /**
     * Executes the operation with the specified parameters.
     *
     * @param arg The arguments of the operation.
     * @see OperationHttp#reset()
     * @see OperationHttp#beforeWorkInBackground()
     * @see OperationHttp#simulateWaiting(Object...)
     * @see OperationHttp#doRequest(Object...)
     */
    @Override
    public boolean performOperation(Object... arg) {
        mLogger.info(String.format("Performing operation. Status: %s", mOperationStatus.name()));
        switch (mOperationStatus) {
            default:
            case UNKNOWN:
            case READY_TO_EXECUTE:
            case WAITING_EXECUTION:
                if (!hasInternet()) {
                    sendBroadcastForNoInternet();
                    return false;
                }
                beforeWorkInBackground();
                if (mConnectionDelay > 0) {
                    simulateWaiting(arg);
                } else {
                    doRequest(arg);
                }
                return true;
            case FINISHED_EXECUTION:
                afterWorkInBackgroundBroadcasts(mResult);
                return false;
            case DOING_EXECUTION:
                beforeWorkInBackgroundBroadcasts();
                return false;
        }
    }

    private boolean hasInternet() {
        ConnectivityManager cm = (ConnectivityManager) OperationApp.getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            mLogger.info("No internet");
            return Boolean.FALSE;
        }
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo == null) {
            mLogger.info("No internet");
            return Boolean.FALSE;
        }
        boolean connected = networkInfo.isConnected();
        if (connected)
            mLogger.info("No internet");
        else
            mLogger.info("Has internet");
        return connected;
    }

    //region Broadcast

    /**
     * It allows you to add extras to the intent that will be received by the receiver.
     * Is triggered only if you can connect to the server.
     */
    protected abstract void addExtrasForResultOk(Intent intent);

    /**
     * It allows you to add extras to the intent that will be received by the receiver.
     * Is triggered only if an error has occurred.
     */
    protected abstract void addExtrasForResultError(Intent intent);

    public void sendBroadcastForNoInternet() {
        mLogger.info("Sending broadcast for no internet");
        Intent intent = new Intent();
        intent.putExtra(OperationResult.EXTRA_STATUS, OperationResult.NO_INTERNET.name);

        String actionWithId = OperationBroadcastReceiver.getActionForNoInternet(this);
        intent.setAction(actionWithId);
        LocalBroadcastManager.getInstance(OperationApp.getInstance()).sendBroadcast(intent);

        String actionWithOutID = OperationBroadcastReceiver.getActionForNoInternet(getClass());
        if (!actionWithId.equals(actionWithOutID)) {
            intent.setAction(actionWithOutID);
            LocalBroadcastManager.getInstance(OperationApp.getInstance()).sendBroadcast(intent);
        }
    }

    public void sendBroadcastForStart() {
        mLogger.info("Sending broadcast for start");
        Intent intent = new Intent();
        intent.putExtra(OperationResult.EXTRA_STATUS, OperationResult.START.name);

        String actionWithId = OperationBroadcastReceiver.getActionForStart(this);
        intent.setAction(actionWithId);
        LocalBroadcastManager.getInstance(OperationApp.getInstance()).sendBroadcast(intent);

        String actionWithOutID = OperationBroadcastReceiver.getActionForStart(getClass());
        if (!actionWithId.equals(actionWithOutID)) {
            intent.setAction(actionWithOutID);
            LocalBroadcastManager.getInstance(OperationApp.getInstance()).sendBroadcast(intent);
        }
    }

    public void sendBroadcastForOk() {
        mLogger.info("Sending broadcast for success");
        Intent intent = new Intent();
        intent.putExtra(OperationResult.EXTRA_STATUS, OperationResult.OK.name);
        addExtrasForResultOk(intent);

        String actionWithId = OperationBroadcastReceiver.getActionForOk(this);
        intent.setAction(actionWithId);
        LocalBroadcastManager.getInstance(OperationApp.getInstance()).sendBroadcast(intent);

        String actionWithOutID = OperationBroadcastReceiver.getActionForOk(getClass());
        if (!actionWithId.equals(actionWithOutID)) {
            intent.setAction(actionWithOutID);
            LocalBroadcastManager.getInstance(OperationApp.getInstance()).sendBroadcast(intent);
        }
    }

    public void sendBroadcastForError() {
        mLogger.info("Sending broadcast for error");
        Intent intent = new Intent();
        intent.putExtra(OperationResult.EXTRA_STATUS, OperationResult.ERROR.name);
        addExtrasForResultError(intent);

        String actionWithId = OperationBroadcastReceiver.getActionForError(this);
        intent.setAction(actionWithId);
        LocalBroadcastManager.getInstance(OperationApp.getInstance()).sendBroadcast(intent);

        String actionWithOutID = OperationBroadcastReceiver.getActionForError(getClass());
        if (!actionWithId.equals(actionWithOutID)) {
            intent.setAction(actionWithOutID);
            LocalBroadcastManager.getInstance(OperationApp.getInstance()).sendBroadcast(intent);
        }
    }

    public void sendBroadcastForFinish() {
        mLogger.info("Sending broadcast for finished");
        Intent intent = new Intent();
        intent.putExtra(OperationResult.EXTRA_STATUS, OperationResult.FINISH.name);

        String actionWithId = OperationBroadcastReceiver.getActionForFinish(this);
        intent.setAction(actionWithId);
        LocalBroadcastManager.getInstance(OperationApp.getInstance()).sendBroadcast(intent);

        String actionWithOutID = OperationBroadcastReceiver.getActionForFinish(getClass());
        if (!actionWithId.equals(actionWithOutID)) {
            intent.setAction(actionWithOutID);
            LocalBroadcastManager.getInstance(OperationApp.getInstance()).sendBroadcast(intent);
        }
    }
    //endregion
    //endregion

    //region Lazy work

    /**
     * Returns to the original state of the operation
     */
    public void reset() {
        mLogger.info("Resetting");
        mOperationStatus = OperationStatus.READY_TO_EXECUTE;
    }

    /**
     * Execute the request with the strategy
     *
     * @see IOperationStrategy
     */
    private void doRequest(Object... arg) {
        mLogger.info("Doing request");
        mStrategy = getStrategy(arg);
        mStrategy.doRequest(this);
    }

    /**
     * Simulate a delay in the connection and then execute the request
     *
     * @see OperationHttp#setConnectionDelay(int)
     * @see OperationHttp#setConnectionDelay(long)
     * @see OperationHttp#doRequest(Object...)
     */
    private void simulateWaiting(final Object... arg) {
        mLogger.info("Simulating waiting");
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    Thread.sleep(mConnectionDelay.intValue());
                } catch (InterruptedException e) {
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                mLogger.info("Finished simulating waiting");
                doRequest(arg);
            }
        }.execute((Void) null);
    }

    /**
     * Called when the operation is finished but before the receiver is notified.
     *
     * @param duration The duration of the request from which start until finish
     */
    protected void onOperationFinish(Long duration) {
        mLogger.info("On operation finish");
    }
    //endregion

    //region Hard work

    /**
     * Returns a strategy for server connection
     *
     * @param arg The arguments specified in performOperation()
     */
    protected abstract IOperationStrategy getStrategy(Object... arg);

    /**
     * Initialize variables and send the broadcast to notify that the operation starts
     *
     * @see OperationHttp#sendBroadcastForStart()
     */
    private void beforeWorkInBackground() {
        mLogger.info("Before work in background");
        mOperationStatus = OperationStatus.DOING_EXECUTION;
        timeInit = Calendar.getInstance().getTimeInMillis();
        beforeWorkInBackgroundBroadcasts();
    }

    private void beforeWorkInBackgroundBroadcasts() {
        sendBroadcastForStart();
    }

    /**
     * Analyze the parameters to determine what would do
     *
     * @param anException The exception occurred
     * @param aHttpCode   The httpCode obtained
     * @param aString     The aString obtained
     * @see OperationHttp#analyzeException(Exception)
     */
    private Boolean workInBackground(Exception anException, int aHttpCode, String aString) {
        mLogger.info("Work in background");
        if (anException != null) {
            analyzeException(anException);
            return false;
        } else {
            try {
                if (!analyzeResult(new HttpOperationResponse(aHttpCode, aString)))
                    throw new UnexpectedResponseException();
            } catch (Exception e2) {
                mLogger.log(Level.INFO, "Error in analyzeResult: " + e2.getMessage(), e2);
                analyzeException(e2);
                return false;
            }
            return true;
        }
    }

    /**
     * Update the variables
     *
     * @param aResult Boolean that notify the result of the operation
     */
    private void afterWorkInBackground(Boolean aResult) {
        mLogger.info("After work in background");
        mResult = aResult;
        mOperationStatus = OperationStatus.FINISHED_EXECUTION;
        if (timeInit != null) {
            long timeFinish = Calendar.getInstance().getTimeInMillis();
            long difference = timeFinish - timeInit;
            onOperationFinish(difference);
        }
        afterWorkInBackgroundBroadcasts(aResult);
    }

    /**
     * Send the broadcast to notify that the operation finished
     *
     * @param aResult Boolean that notify the result of the operation
     * @see OperationHttp#onOperationFinish(Long)
     * @see OperationHttp#sendBroadcastForOk()
     * @see OperationHttp#sendBroadcastForError()
     */
    private void afterWorkInBackgroundBroadcasts(Boolean aResult) {
        if (aResult) {
            sendBroadcastForOk();
        } else {
            sendBroadcastForError();
        }
        sendBroadcastForFinish();
    }
    //endregion

    //region Android lifecycle
    public void onCreate(Bundle savedInstanceState) {
        mLogger.info("On create");
        if (savedInstanceState != null)
            //Restauro los datos necesario de la operacion
            onRestoreInstanceState(savedInstanceState);
    }

    public void onSaveInstanceState(Bundle outState) {
        mLogger.info("On save instance state");
        if (timeInit != null)
            outState.putLong(SAVE_INSTANCE_TIME_INIT, timeInit);
        outState.putString(SAVE_INSTANCE_ID, id);

        if (mResult != null)
            outState.putSerializable(SAVE_INSTANCE_RESULT, mResult);
        outState.putSerializable(SAVE_INSTANCE_STATE, mOperationStatus);
        if (mStrategy != null)
            outState.putSerializable(SAVE_INSTANCE_STRATEGY, mStrategy);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        mLogger.info("On restore instance state");
        id = savedInstanceState.getString(SAVE_INSTANCE_ID);
        mOperationStatus = (OperationStatus) savedInstanceState.getSerializable(SAVE_INSTANCE_STATE);
        if (savedInstanceState.containsKey(SAVE_INSTANCE_STRATEGY)) {
            mStrategy = (IOperationStrategy) savedInstanceState.getSerializable(SAVE_INSTANCE_STRATEGY);
            mStrategy.updateCallback(this);
        }
        if (savedInstanceState.containsKey(SAVE_INSTANCE_TIME_INIT))
            timeInit = savedInstanceState.getLong(SAVE_INSTANCE_TIME_INIT);
        if (savedInstanceState.containsKey(SAVE_INSTANCE_RESULT))
            mResult = savedInstanceState.getBoolean(SAVE_INSTANCE_RESULT);
    }
    //endregion

    //region Getters & Setters

    /**
     * @return The status of the operation
     * @see OperationStatus
     */
    public OperationStatus getStatus() {
        return mOperationStatus;
    }

    /**
     * Defines a time delay for the operation
     *
     * @see OperationHttp#simulateWaiting(Object...)
     */
    protected void setConnectionDelay(int duration) {
        this.mConnectionDelay = (long) duration;
    }

    /**
     * Defines a time delay for the operation
     *
     * @see OperationHttp#simulateWaiting(Object...)
     */
    protected void setConnectionDelay(long duration) {
        this.mConnectionDelay = duration;
    }

    /**
     * @return The id of the operation
     */
    public String getId() {
        return id;
    }

    /**
     * Defines an id for the operation
     *
     * @see OperationHttp#OperationHttp(String)
     */
    public void setId(String id) {
        if (id == null)
            id = "";
        this.id = id;
    }
    //endregion

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OperationHttp operationHttp = (OperationHttp) o;

        if (!TextUtils.isEmpty(id) ? !id.equals(operationHttp.id) : !TextUtils.isEmpty(operationHttp.id))
            return false;
        if (timeInit != null ? !timeInit.equals(operationHttp.timeInit) : operationHttp.timeInit != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = timeInit != null ? timeInit.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        return result;
    }
}