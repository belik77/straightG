package com.plutus.straightg;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

import com.mbientlab.bletoolbox.scanner.BleScannerFragment;
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
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.Settings;

import java.util.UUID;

import bolts.Continuation;
import bolts.Task;
import io.reactivex.functions.Consumer;

import static java.security.AccessController.getContext;

public class MainActivity extends AppCompatActivity implements ServiceConnection, BleScannerFragment.ScannerCommunicationBus {

    private final static String TAG = MainActivity.class.getCanonicalName();

    private final static UUID[] serviceUuids;

    static {
        serviceUuids= new UUID[] {
                MetaWearBoard.METAWEAR_GATT_SERVICE,
                MetaWearBoard.METABOOT_SERVICE
        };
    }

    private static final float[] MMA845Q_RANGES= {2.f, 4.f, 8.f}, BOSCH_RANGES = {2.f, 4.f, 8.f, 16.f};
    private static final float INITIAL_RANGE= 2.f, ACC_FREQ= 50.f;

    //TODO: change
    private final String MW_MAC_ADDRESS= "C3:93:24:1C:A9:23";
    //private final String MW_MAC_ADDRESS= "DC:AE:23:CA:D4:27";
    private BtleService.LocalBinder serviceBinder;
    private MetaWearBoard mwBoard;
    private boolean boardReady;
    private Accelerometer accelerometer;
    private Detector detector;
    private long lastVibrateTime;

    private Led led;


    static void setConnInterval(Settings settings) {
        if (settings != null) {
            Settings.BleConnectionParametersEditor editor = settings.editBleConnParams();
            if (editor != null) {
                editor.maxConnectionInterval(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 11.25f : 7.5f)
                        .commit();
            }
        }
    }

    public static Task<Void> reconnect(final MetaWearBoard board) {
        return board.connectAsync()
                .continueWithTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> task) throws Exception {
                        if (task.isFaulted()) {
                            return reconnect(board);
                        } else if (task.isCancelled()) {
                            return task;
                        }
                        return Task.forResult(null);
                    }
                });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ///< Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);

        BluetoothAdapter btAdapter=
                ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        assert(btAdapter != null && btAdapter.isEnabled());

    }



    public void initBoard() {
        final BluetoothManager btManager=
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice=
                btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);

        // Create a MetaWear board object for the Bluetooth Device
        mwBoard = serviceBinder.getMetaWearBoard(remoteDevice);

        mwBoard.connectAsync()
                .continueWithTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task then(Task task) throws Exception {
                        if (task.isCancelled()) {
                            return task;
                        }
                        return task.isFaulted() ? reconnect(mwBoard) : Task.forResult(null);
                    }
                })
                .continueWith(new Continuation<Void, Object>() {
                    @Override
                    public Object then(Task<Void> task) throws Exception {
                        if (!task.isCancelled()) {
                            setConnInterval(mwBoard.getModule(Settings.class));
                            Log.i(TAG,"Connected");

                            //alert the user that the board is connected
                            //board_is_connected();

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    boardReady= true;
                                    boardReady();
                                }
                            });
                        }
                        return null;
                    }
                });
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

    private void board_is_connected() {
        //TODO: handle properly
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setTitle(R.string.board_connected)
                .setCancelable(false)
                .setPositiveButton(R.string.label_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                }).create();
        alertDialog.show();
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
        detector = new Detector();
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

                        detector.onNewData(new AccData(x,y,z));
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

        detector.getFlowable().subscribe(new Consumer<AccResult>() {
            @Override
            public void accept(@io.reactivex.annotations.NonNull AccResult result) throws Exception {
                final double BUFFER_THRESHOLD = 0.05;

                boolean xOverThreshold = Math.abs(result.avgX) > BUFFER_THRESHOLD;
                boolean yOverThreshold = Math.abs(result.avgY) > BUFFER_THRESHOLD;
                float avgAVG = Math.abs(result.avgY) - Math.abs(result.avgX);

                Log.i(TAG+"DATA",result.toString());
                if (xOverThreshold && yOverThreshold) {
                    if (Math.abs(result.avgY) * 1.5 <= Math.abs(result.avgX)) {
                        if (System.currentTimeMillis() - lastVibrateTime >= 2000) {
                       /*           *//**//*  handler = new Timer();
                            timer.scheduleTask(new Timer.Task() {
                                @Override
                                public void run() {
                                    drawRed = false;
                                }
                            }, 0.5f);*//**//*
                        }*/
                            setLedColor(true);
                            Log.i("BAD ACCDATA", result.toString());
                        } else {
                            setLedColor(false);
                            Log.i("GOOD ACCDATA", result.toString());
                        }
                    }
                }
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

    public void stopLed() {
        try {
            led.stop(true);
        } catch (Exception ignored) {

        }
    }

    public void setLedColor(boolean isRed) {
        try {
            led = mwBoard.getModule(Led.class);
            if(isRed) {
                led.editPattern(Led.Color.RED);
            } else {
                led.editPattern(Led.Color.GREEN);
            }

            led.play();
        } catch (Exception e) {
            Log.i(TAG,"Error setting led color");
        }
    }

    @Override
    public UUID[] getFilterServiceUuids() {
        return serviceUuids;
    }

    @Override
    public long getScanDuration() {
        return 10000L;
    }

    @Override
    public void onDeviceSelected(BluetoothDevice device) {
        initBoard();
    }
}