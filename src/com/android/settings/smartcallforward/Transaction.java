package com.android.settings.smartcallforward;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneFactory;

import java.util.ArrayList;
import java.util.Stack;

public class Transaction extends Thread {
    static final String TAG = "SmartForward.Transaction";

    private static int sNextSerial = 0;
    private static final int OPERATION_DELAY_TIME = 500;
    private Handler mHandler;
    private ArrayList<Action> mActions;
    private Stack<Action> mExecuted;
    private int mSerial;
    private Callback mCallback;
    private int mIndex = 0;
    private boolean mRollback = true;
    private boolean mCommitted;
    private boolean mSuccess;
    private TransactionException mLastException;

    interface Callback {
        void onComplete(Transaction t);

        void onError(Transaction t);
    }

    public static Transaction newTransaction(boolean noRevert) {
        return new Transaction(sNextSerial++, noRevert);
    }

    private Transaction(int s, boolean noRevert) {
        mSerial = s;
        mActions = new ArrayList<Action>();
        mExecuted = new Stack<Action>();
        mHandler = new MainThreadHandler(Looper.getMainLooper());
        mRollback = !noRevert;
    }

    public Transaction setCallWaiting(int phoneId, boolean enable) {
        if (mCommitted) {
            throw new IllegalStateException("Transaction already commited");
        }
        addAction(new CwAction(mHandler, mIndex++, phoneId, enable));
        return this;
    }

    public Transaction setCallForward(int phoneId, boolean enable, String number) {
        if (mCommitted) {
            throw new IllegalStateException("Transaction already commited");
        }
        addAction(new CfAction(mHandler, mIndex++, phoneId, enable, number));
        return this;
    }

    public void commit() {
        if (mCommitted) {
            throw new IllegalStateException("Transaction already commited");
        }
        mCommitted = true;
        start();
    }

    public void commitNoRollback() {
        mRollback = false;
        commit();
    }

    public void setCallback(Callback cb) {
        mCallback = cb;
    }

    public Callback getCallback() {
        return mCallback;
    }

    public TransactionException getLastException() {
        return mLastException;
    }

    @Override
    public String toString() {
        return TAG + "#" + mSerial;
    }

    public void dump() {
        int N = mActions.size();
        Log.d(TAG, "Dump of " + this);
        for (int i = 0; i < N; i++) {
            Action a = mActions.get(i);
            Log.d(TAG, "  " + a);
        }
    }

    @Override
    public void run() {
        int N = mActions.size();

        mSuccess = true;
        mLastException = null;
        for (int i = 0; i < N; i++) {
            try {
                Action a = mActions.get(i);
                TransactionException e = a.execute();
                if (e == null) {
                    mExecuted.push(a);
                } else {
                    mLastException = e;
                    mSuccess = false;
                    if (mRollback) {
                        break;
                    }
                }
                sleep(OPERATION_DELAY_TIME);
            } catch (InterruptedException e) {
                mSuccess = false;
                break;
            }
        }

        if (!mSuccess) {
            if (mRollback) {
                rollback();
            }
        }
        deliverResult(mSuccess, mLastException);
    }

    private synchronized void addAction(Action a) {
        mActions.add(a);
    }

    private void rollback() {
        while (!mExecuted.empty()) {
            mExecuted.pop().revert();
        }
    }

    private void deliverResult(boolean positive, TransactionException e) {
        Message msg = mHandler.obtainMessage(MainThreadHandler.MSG_TRANSACTION_RESULT, this);
        AsyncResult.forMessage(msg, Boolean.valueOf(positive), e);
        msg.sendToTarget();
    }
}

abstract class Action {
    private Handler mMainHandler;
    protected int mIndex;
    protected int mPhoneId;
    protected int mCmd = MainThreadHandler.CMD_INVALID;

    public Action(Handler h, int index, int phoneId, int cmd) {
        mMainHandler = h;
        mIndex = index;
        mPhoneId = phoneId;
        mCmd = cmd;
    }

    public TransactionException execute() {
        Log.d(Transaction.TAG, "Executing " + this);
        RequestInfo request = makeRequest();
        AsyncResult ar = (AsyncResult) sendRequest(request);
        if (ar.exception != null) {
            Log.e(Transaction.TAG, this.toString() + " got Exception " + ar.exception);
            // TODO: convert the exception
            String errorMsg = "ERROR";
            CommandException e = null;
            if (ar.exception instanceof CommandException) {
                e = (CommandException) ar.exception;
            }
            return new TransactionException(mCmd, mPhoneId, errorMsg, e);
        }
        return null;
    }

    public void revert() {
        Log.d(Transaction.TAG, "Reverting " + this);
        RequestInfo request = makeRevertRequest();
        sendRequest(request);
    }

    protected abstract RequestInfo makeRequest();

    protected abstract RequestInfo makeRevertRequest();

    private Object sendRequest(RequestInfo request) {
        Message msg = mMainHandler.obtainMessage(mCmd, request);
        msg.sendToTarget();

        synchronized (request) {
            while (request.result == null) {
                try {
                    request.wait();
                } catch (InterruptedException e) {
                }
            }
        }
        return request.result;
    }
}

class CwAction extends Action {
    private boolean mEnable;

    public CwAction(Handler h, int index, int phoneId, boolean enable) {
        super(h, index, phoneId, MainThreadHandler.CMD_CW_ACTION);
        mEnable = enable;
    }

    @Override
    public RequestInfo makeRequest() {
        RequestInfo ri = new RequestInfo();
        ri.phoneId = mPhoneId;
        ri.enable = mEnable;
        ri.argument = null;
        return ri;
    }

    @Override
    public RequestInfo makeRevertRequest() {
        RequestInfo ri = new RequestInfo();
        ri.phoneId = mPhoneId;
        ri.enable = !mEnable;
        ri.argument = null;
        return ri;
    }

    @Override
    public String toString() {
        return "[" + mIndex + "] Phone " + mPhoneId + " setCallWaiting(" + mEnable + ")";
    }
}

class CfAction extends Action {
    private boolean mEnable;
    private String mNumber;

    public CfAction(Handler h, int index, int phoneId, boolean enable, String number) {
        super(h, index, phoneId, MainThreadHandler.CMD_CF_ACTION);
        mEnable = enable;
        mNumber = number;
    }

    @Override
    public RequestInfo makeRequest() {
        RequestInfo ri = new RequestInfo();
        ri.phoneId = mPhoneId;
        ri.enable = mEnable;
        ri.argument = mNumber;
        return ri;
    }

    @Override
    public RequestInfo makeRevertRequest() {
        RequestInfo ri = new RequestInfo();
        ri.phoneId = mPhoneId;
        ri.enable = !mEnable;
        ri.argument = mNumber;
        return ri;
    }

    @Override
    public String toString() {
        return "[" + mIndex + "] Phone " + mPhoneId + " setCallForwarding(" + mEnable +
                ", " + mNumber + ")";
    }
}

final class RequestInfo {
    public int phoneId;
    public boolean enable;
    public Object argument;
    public Object result;
}

final class MainThreadHandler extends Handler {
    public static final int CMD_INVALID = -1;
    public static final int CMD_CW_ACTION = 0;
    public static final int CMD_CF_ACTION = 1;
    public static final int EVENT_SET_COMPLETE = 2;
    public static final int MSG_TRANSACTION_RESULT = 3;

    public MainThreadHandler(Looper looper) {
        super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
        Log.d(Transaction.TAG, "handleMessage msg.what=" + msg.what);
        switch (msg.what) {
            case CMD_CW_ACTION:
                RequestInfo cwRequest = (RequestInfo) msg.obj;
                Message cwOnComplete = obtainMessage(EVENT_SET_COMPLETE, cwRequest);
                PhoneFactory.getPhone(cwRequest.phoneId).setCallWaiting(
                        cwRequest.enable, cwOnComplete);
                break;
            case CMD_CF_ACTION:
                RequestInfo cfRequest = (RequestInfo) msg.obj;
                Message cfOnComplete = obtainMessage(EVENT_SET_COMPLETE, cfRequest);
                int action = cfRequest.enable ? CommandsInterface.CF_ACTION_REGISTRATION :
                        CommandsInterface.CF_ACTION_DISABLE;
                int timerSeconds = 0;
                PhoneFactory.getPhone(cfRequest.phoneId).setCallForwardingOption(action,
                        CommandsInterface.CF_REASON_NOT_REACHABLE,
                        (String) cfRequest.argument, timerSeconds, cfOnComplete);
                break;
            case EVENT_SET_COMPLETE:
                AsyncResult setAr = (AsyncResult) msg.obj;
                RequestInfo request = (RequestInfo) setAr.userObj;

                synchronized (request) {
                    request.result = setAr;
                    request.notifyAll();
                }
                break;
            case MSG_TRANSACTION_RESULT:
                AsyncResult ar = (AsyncResult) msg.obj;
                boolean positive = (Boolean) ar.result;
                Transaction tr = (Transaction) ar.userObj;
                Transaction.Callback cb = tr.getCallback();
                if (cb != null) {
                    if (positive) {
                        cb.onComplete(tr);
                    } else {
                        cb.onError(tr);
                    }
                }
                break;
            default:
                break;
        }
    }
}

final class TransactionException extends Exception {
    public int errorCode;
    public int argument;
    public CommandException exception;

    public TransactionException(int err, int arg, String msg, CommandException e) {
        super(msg);
        errorCode = err;
        argument = arg;
        exception = e;
    }
}
