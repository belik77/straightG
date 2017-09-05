package com.plutus.straightg;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ViewGroup;

import com.mbientlab.metawear.AsyncDataProducer;
import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.AccelerometerBosch;
import com.mbientlab.metawear.module.AccelerometerMma8452q;

import bolts.Continuation;
import bolts.Task;

import static java.security.AccessController.getContext;

public class MainActivity extends AppCompatActivity implements ServiceConnection {

    private static final float[] MMA845Q_RANGES= {2.f, 4.f, 8.f}, BOSCH_RANGES = {2.f, 4.f, 8.f, 16.f};
    private static final float INITIAL_RANGE= 2.f, ACC_FREQ= 50.f;

    //TODO: change
    private final String MW_MAC_ADDRESS= "EC:2C:09:81:22:AC";
    private BtleService.LocalBinder serviceBinder;
    private MetaWearBoard mwBoard;
    private boolean boardReady;
    private Accelerometer accelerometer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ///< Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);
    }



    public void initBoard() {
        final BluetoothManager btManager=
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice=
                btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);

        // Create a MetaWear board object for the Bluetooth Device
        mwBoard = serviceBinder.getMetaWearBoard(remoteDevice);
        boardReady= true;
        boardReady();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ///< Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
        clean();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ///< Typecast the binder to the service's LocalBinder class
        serviceBinder = (BtleService.LocalBinder) service;

        initBoard();

    }

    private void boardReady() {
        try {
            accelerometer = mwBoard.getModuleOrThrow(Accelerometer.class);
            setup();
        } catch (UnsupportedModuleException e) {
            e.printStackTrace();
        }
    }

    private void unsupportedModule() {
        //TODO: handle properly
        new AlertDialog.Builder(this).setTitle(R.string.title_error)
                .setCancelable(false)
                .setPositiveButton(R.string.label_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .create()
                .show();
    }


    @Override
    public void onServiceDisconnected(ComponentName componentName) { }

    protected void setup() {
        Accelerometer.ConfigEditor<?> editor = accelerometer.configure();
        int rangeIndex = 0;
        editor.odr(ACC_FREQ);
        if (accelerometer instanceof AccelerometerBosch) {
            editor.range(BOSCH_RANGES[rangeIndex]);
        } else if (accelerometer instanceof AccelerometerMma8452q) {
            editor.range(MMA845Q_RANGES[rangeIndex]);
        }
        editor.commit();

        final float samplePeriod = 1 / accelerometer.getOdr();

        final AsyncDataProducer producer = accelerometer.packedAcceleration() == null ?
                accelerometer.packedAcceleration() :
                accelerometer.acceleration();
        producer.addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        final Acceleration value = data.value(Acceleration.class);
                        //TODO: handle accelerometer data
                        float x = value.x();
                        float y = value.y();
                        float z = value.z();
                    }
                });
            }
        }).continueWith(new Continuation<Route, Object>() {
            @Override
            public Object then(Task<Route> task) throws Exception {
                Route streamRoute = task.getResult();
                producer.start();
                accelerometer.start();
                return null;
            }
        });
    }

    protected void clean() {
        if(accelerometer != null) {
            accelerometer.stop();

            (accelerometer.packedAcceleration() == null ?
                    accelerometer.packedAcceleration() :
                    accelerometer.acceleration()
            ).stop();
        }
    }

}