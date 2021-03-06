package com.globallogic.futbol.example.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.globallogic.futbol.example.R;

/**
 * Created by Ezequiel Sanz on 11/05/15.
 * GlobalLogic | ezequiel.sanz@globallogic.com
 */
public class PutPageFragment extends Fragment implements View.OnClickListener {
    protected ICallback mCallback;

    public static PutPageFragment newInstance() {
        return new PutPageFragment();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (ICallback.class.isInstance(activity))
            mCallback = (ICallback) activity;
        else
            throw new RuntimeException("The activity " + activity.getClass().getSimpleName() + " must implement " + ICallback.class.getName());
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallback = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_put_page, container, false);
        rootView.findViewById(R.id.fragment_put_page_update_item).setOnClickListener(this);
        return rootView;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fragment_put_page_update_item:
                mCallback.onExamplePutSingleString();
                break;
        }
    }

    public interface ICallback {
        void onExamplePutSingleString();
    }
}