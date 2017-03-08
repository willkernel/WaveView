package com.willkernel.app.waveview.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.willkernel.app.waveview.view.WaveView2;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new WaveView2(this));
    }
}