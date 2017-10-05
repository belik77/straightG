package com.plutus.straightg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;

class Detector {
    private static final int BUFFER_SIZE = 40;
    private static final float NOISE_DELTA_THRESHOLD = 0.5f;

    private final Flowable<AccData> flowable;
    private FlowableEmitter<AccData> emitter;

    public Detector() {
        flowable = Flowable.create(new FlowableOnSubscribe<AccData>() {
            @Override
            public void subscribe(@NonNull FlowableEmitter<AccData> e) throws Exception {
                emitter = e;
            }
        },BackpressureStrategy.LATEST);
    }

    public void onNewData(AccData accData) {
        if(!emitter.isCancelled())
            emitter.onNext(accData);
    }

    public Flowable<AccResult> getFlowable() {
        return flowable.filter(new Predicate<AccData>() {
            @Override
            public boolean test(@NonNull AccData accData) throws Exception {
                return true;
            }
        }).buffer(BUFFER_SIZE)
                .map(new Function<List<AccData>, AccResult>() {
                    @Override
                    public AccResult apply(@NonNull List<AccData> accDatas) throws Exception {

                        AccResult data = new AccResult();

                        AccData last = accDatas.get(0);

                        float sumX = 0, sumY = 0, sumZ = 0;

                        for (int i = 1; i < accDatas.size(); i++) {
                            AccData current = accDatas.get(i);

                            //Compute deltas between pairs
                            float deltaX = last.x - current.x;
                            float deltaY = last.y - current.y;
                            float deltaZ = last.z - current.z;

                            //Remove noise
                            if (deltaX <= NOISE_DELTA_THRESHOLD) {
                                deltaX = 0;
                            }
                            if (deltaY <= NOISE_DELTA_THRESHOLD) {
                                deltaY = 0;
                            }
                            if (deltaZ <= NOISE_DELTA_THRESHOLD) {
                                deltaZ = 0;
                            }

                            data.deltaX += deltaX;
                            data.deltaY += deltaY;
                            data.deltaZ += deltaZ;
                            last = current;
                        }

                        List<Float> xOnly = new ArrayList<Float>();
                        List<Float> yOnly = new ArrayList<Float>();
                        List<Float> zOnly = new ArrayList<Float>();

                        for (AccData d : accDatas) {

                            xOnly.add(d.x);
                            yOnly.add(d.y);
                            zOnly.add(d.z);

                            sumX += Math.abs(d.x);
                            sumY += Math.abs(d.y);
                            sumZ += Math.abs(d.z);
                        }

                        data.avgX = sumX / BUFFER_SIZE;
                        data.avgY = sumY / BUFFER_SIZE;
                        data.avgZ = sumZ / BUFFER_SIZE;

                        data.sumX = sumX;
                        data.sumY = sumY;
                        data.sumZ = sumZ;

                        Collections.sort(xOnly);
                        Collections.sort(yOnly);
                        Collections.sort(zOnly);

                        data.medianX = xOnly.get(xOnly.size() / 2);
                        data.medianY = yOnly.get(yOnly.size() / 2);
                        data.medianZ = zOnly.get(zOnly.size() / 2);

                        return data;
                    }
                });
    }



}
