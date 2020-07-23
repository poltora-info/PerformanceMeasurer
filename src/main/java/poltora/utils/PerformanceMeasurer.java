/*
 * Copyright (c) 2020 Oleg Poltoratskii www.poltora.info
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package poltora.utils;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.log4j.Logger;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Measuring device (measurer) for measuring performance
 *
 * @author Oleg Poltoratskii ( www.poltora.info )
 */
public class PerformanceMeasurer {

    private static final String SUCCESS_NAME = "success";
    private static final String ERROR_NAME = "error";
    private static final String FAIL_NAME = "fail";
    private static Logger LOGGER = Logger.getLogger(PerformanceMeasurer.class);
    private static Map<String, PerformanceMeasurer> measurers = new ConcurrentHashMap<>();
    private static Map<String, PerformanceMeasurer> measurersOld = new ConcurrentHashMap<>();
    private static ScheduledExecutorService scheduler;
    private static int time = 15;
    private static TimeUnit timeUnit = TimeUnit.SECONDS;

    private Logger logger;
    private String name;
    private Map<String, Sensor> sensors;
    private int possibleSize;
    private long startTime;

    private ThreadLocal<Long> stepStartTime;
    private AtomicLong stepDuration;

    // not state
    private long currentTime;
    private long allDuration;
    private Sensor summarySensor;
    private Sensor throughputSensor;
    private StringBuffer log;

    static {
        PerformanceMeasurer.addShutdownHook();

        PerformanceMeasurer.schedulerInit();
    }

    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                display();
            }
        });
    }

    private static void schedulerInit() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                (Runnable) PerformanceMeasurer::scheduleWork,
                time, time, timeUnit
        );
    }

    @SuppressWarnings("unused")
    public static void setSchedulerTimeout(int time, TimeUnit timeUnit) {
        PerformanceMeasurer.time = time;
        PerformanceMeasurer.timeUnit = timeUnit;

        scheduler.scheduleAtFixedRate(
                (Runnable) PerformanceMeasurer::scheduleWork,
                time, time, timeUnit
        );
    }

    private static void scheduleWork() {
        display();
        purge();
    }

    @SuppressWarnings("Convert2streamapi")
    private static void purge() {
        if (measurers.isEmpty()) return;


        long curTime = System.currentTimeMillis();
        int maxSleepingTime = 24 * 60 * 60 * 1000;

        for (PerformanceMeasurer measurer : measurers.values()) {
            if (measurer.currentTime != 0 && measurer.currentTime < curTime - maxSleepingTime) {
                LOGGER.debug(String.format("Purging old measurers [%s]", measurer.name));
                measurers.remove(measurer.name);
                measurersOld.remove(measurer.name);
            }
        }
    }

    public static void display() {
        if (measurers.isEmpty()) return;


        List<Map.Entry<String, PerformanceMeasurer>> list = new ArrayList<>(measurers.entrySet());
        Collections.sort(list, (o1, o2) -> {
            long startTime1 = o1.getValue().startTime;
            long startTime2 = o2.getValue().startTime;

            return startTime1 > startTime2 ? 1 : startTime1 < startTime2 ? -1 : 0;
        });

        for (Map.Entry<String, PerformanceMeasurer> entry : list) {
            String name = entry.getKey();
            PerformanceMeasurer measurer = entry.getValue();

            measurersOld.put(
                    name,
                    measurer.display(
                            measurersOld.get(name)
                    )
            );
        }
    }

    public static PerformanceMeasurer get() {

        return get(
                Thread.currentThread().getStackTrace()[2].getClassName()
        );
    }

    public static PerformanceMeasurer getByMethodName() {

        return get(
                String.format(
                        "%s.%s()",
                        Thread.currentThread().getStackTrace()[2].getClassName(),
                        Thread.currentThread().getStackTrace()[2].getMethodName()
                )
        );
    }

    public static PerformanceMeasurer get(Class clazz) {
        return get(
                clazz.getName()
        );
    }

    public static PerformanceMeasurer get(String name) {
        return measurers.computeIfAbsent(name, k -> new PerformanceMeasurer(name));
    }

    private PerformanceMeasurer(String name) {
        logger = Logger.getLogger(name);
        this.name = name;
        startTime = System.currentTimeMillis();

        stepStartTime = new ThreadLocal<Long>() {
            @Override
            protected Long initialValue() {
                return 0L;
            }
        };
        stepDuration = new AtomicLong();

        sensors = new ConcurrentHashMap<>();


        summarySensor = new Sensor("sum");
        throughputSensor = new Sensor("r/s");
    }

    private PerformanceMeasurer(PerformanceMeasurer other) {
        this.logger = other.logger;
        this.name = other.name;
        this.sensors = other.sensors;
        this.possibleSize = other.possibleSize;
        this.startTime = other.startTime;
        this.stepStartTime = other.stepStartTime;
        this.stepDuration = other.stepDuration;
        this.allDuration = other.allDuration;
        this.summarySensor = other.summarySensor;
        this.throughputSensor = other.throughputSensor;
        this.log = other.log;
        this.currentTime = other.currentTime;
    }

    private PerformanceMeasurer newClone() {

        PerformanceMeasurer clone = new PerformanceMeasurer(this);
        clone.stepStartTime = new ThreadLocal<>();
        clone.stepStartTime.set(stepStartTime.get());

        clone.stepDuration = new AtomicLong(stepDuration.get());

        clone.sensors = new HashMap<>();
        for (Map.Entry<String, Sensor> entry : sensors.entrySet()) {
            clone.sensors.put(
                    entry.getKey(),
                    entry.getValue().newClone()
            );
        }


        return clone;
    }

    private PerformanceMeasurer display(PerformanceMeasurer measurerOld) {
        if (!this.isUpdated(measurerOld)) {
            return this.newClone();
        }


        // snapshot - cloning
        PerformanceMeasurer measurer = this.newClone();


        // time snapshot
        measurer.timeSnapshot();


        // init previous
        if (measurerOld == null) {
            measurerOld = new PerformanceMeasurer(name);
        }

        for (Sensor sensor : sensors.values()) {
            measurerOld.getSensor(sensor.name);
        }


        // make summary
        measurer.makeSummary();


        // log
        logger.info(
                measurer.log(measurerOld)
        );


        return measurer;
    }

    private Sensor getSensor(String name) {
        return sensors.computeIfAbsent(name, k -> new Sensor(name));
    }

    private void timeSnapshot() {
        currentTime = System.currentTimeMillis();
    }

    @SuppressWarnings("Convert2streamapi")
    private void makeSummary() {

        if (hasPersonalTimer()) {
            allDuration = stepDuration.get();
        } else {
            allDuration = currentTime - startTime;
        }
        if (allDuration == 0) allDuration = 1;

        summarySensor = new Sensor("sum");
        for (Sensor sensor : sensors.values()) {

            if (!sensor.isolated) {
                summarySensor.measure(sensor.take());
            }
        }
        throughputSensor = new Sensor("r/s");
        throughputSensor.measure((int) ((summarySensor.take() * 1000) / allDuration));
    }


    private int take() {
        int sum = 0;
        for (Sensor sensor : sensors.values()) {
            sum += sensor.take();
        }
        return sum;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean isUpdated(PerformanceMeasurer measurerOld) {
        int take = this.take();

        if (take == 0) return false;
        if (measurerOld == null) return true;

        return take != measurerOld.take();
    }

    private String log(PerformanceMeasurer measurerOld) {
        log = new StringBuffer();


        if (hasPersonalTimer())
            log.append(" (personal)");

        logValue(DurationFormatUtils.formatDuration(allDuration, "HH:mm:ss"));

        logForecast(measurerOld);

        logThroughout(measurerOld);

        logCommon(measurerOld);

        logIsolated(measurerOld);


        return log.toString();
    }

    private void logCommon(PerformanceMeasurer measurerOld) {
        int notIsolated = 0;
        for (Sensor sensor : sensors.values()) {
            if (sensor.take() != 0 && !sensor.isolated) {
                notIsolated++;
            }
        }

        for (Sensor sensor : sensors.values()) {
            if (sensor.isolated) continue;


            Sensor sensorOld = measurerOld.sensors.get(sensor.name);

            int logLength;
            if (notIsolated > 1) {
                float percent = (float) sensor.take() * 100 / summarySensor.take();
                logLength = logValue(
                        sensorOld.logLength,
                        sensor.name,
                        sensor.take(),
                        sensor.take() - sensorOld.take(),
                        percent
                );
            } else {
                logLength = logValue(
                        sensorOld.logLength,
                        sensor.name,
                        sensor.take(),
                        sensor.take() - sensorOld.take()
                );
            }
            sensor.logLength = logLength;
        }

        if (notIsolated > 1) {
            int logLength;
            logLength = logValue(
                    measurerOld.summarySensor.logLength,
                    summarySensor.name,
                    summarySensor.take(),
                    summarySensor.take() - measurerOld.summarySensor.take()
            );
            summarySensor.logLength = logLength;
        }
    }

    private void logIsolated(PerformanceMeasurer measurerOld) {

        for (Sensor sensor : sensors.values()) {
            if (!sensor.isolated) continue;


            Sensor sensorOld = measurerOld.sensors.get(sensor.name);

            int logLength;
            logLength = logValue(
                    sensorOld.logLength,
                    sensor.name,
                    sensor.take(),
                    sensor.take() - sensorOld.take()
            );

            sensor.logLength = logLength;
        }
    }

    private void logForecast(PerformanceMeasurer measurerOld) {

        int count = 0;
        long size = 0;

        if (possibleSize != 0) {
            // all sensors forecast
            count = summarySensor.take();
            size = possibleSize;
        } else {
            // exact sensor forecast
            for (Sensor sensor : sensors.values()) {
                if (sensor.possibleSize != 0) {
                    count = sensor.take();
                    size = sensor.possibleSize;
                    break;
                }
            }
        }
        if (size == 0) return;


        float percent = 0;
        long leftTime = 0;

        if (count != 0) {
            percent = (float) count * 100 / size;
            leftTime = (((long) (allDuration / percent) * 100)) - allDuration;
        }


        if (percent == 0 && leftTime == 0) {
            logValue("   ∞    ");
        } else if (percent == 100) {
            if (measurerOld.summarySensor.take() != 0) {
                logValue("   .    ");
            }
        } else {
            logValue(DurationFormatUtils.formatDuration(leftTime, "HH:mm:ss"));
        }


        logValue(4, (int) percent, "%");
    }

    private void logThroughout(PerformanceMeasurer measurerOld) {

        if (summarySensor.take() != 0) {
            int logLength;
            logLength = logValue(
                    measurerOld.throughputSensor.logLength,
                    "r/s",
                    throughputSensor.take(),
                    throughputSensor.take() - measurerOld.throughputSensor.take()
            );

            throughputSensor.logLength = logLength;
        }
    }

    private int logValue(int countLogLength, String name, int value, int delta) {
        return logValue(countLogLength, name, value, delta, null);
    }


    private int logValue(int logLength, String name, int value, int delta, Float percentage) {

        log
                .append("  ")
                .append(name)
                .append(": ")
        ;

        int length = log.length();


        if (percentage != null) {
            DecimalFormat val = new DecimalFormat("0");
            log
                    .append(val.format(percentage))
                    .append("% ")
            ;
        }

        log.append(String.valueOf(value));

        if (value != delta) {
            log
                    .append("(")
                    .append(delta > 0 ? "+" : "")
                    .append(String.valueOf(delta))
                    .append(")")
            ;
        }


        log.append(";");


        int currentLength = log.length() - length;

        if (currentLength < logLength) {
            log.append(StringUtils.repeat(" ", logLength - currentLength));
        }

        if (logLength < currentLength) {
            logLength = currentLength;
        }

        return logLength;
    }

    private int logValue(int logLength, int value, String name) {

        log.append(" ");

        int length = log.length();


        log.append(String.valueOf(value));

        log.append(name);


        int currentLength = log.length() - length;

        if (currentLength < logLength) {
            log.append(StringUtils.repeat(" ", logLength - currentLength));
        }

        if (logLength < currentLength) {
            logLength = currentLength;
        }

        return logLength;
    }

    private void logValue(String value) {
        log
                .append(" ")
                .append(value)
        ;
    }

    public int measure(String name) {
        return getSensor(name).measure();
    }

    public int success() {
        return getSensor(SUCCESS_NAME).measure();
    }

    public int error() {
        return getSensor(ERROR_NAME).measure();
    }

    public int fail() {
        return getSensor(FAIL_NAME).measure();
    }

    @SuppressWarnings("unused")
    public int measure(String name, int delta) {
        return getSensor(name).measure(delta);
    }

    @SuppressWarnings("unused")
    public int success(int delta) {
        return getSensor(SUCCESS_NAME).measure(delta);
    }

    @SuppressWarnings("unused")
    public int error(int delta) {
        return getSensor(ERROR_NAME).measure(delta);
    }

    @SuppressWarnings("unused")
    public int fail(int delta) {
        return getSensor(FAIL_NAME).measure(delta);
    }

    public void possibleSize(int size) {
        this.possibleSize = size;
    }

    @SuppressWarnings("unused")
    public void setIsolated(String name) {
        Sensor sensor = getSensor(name);
        sensor.isolated = true;
    }

    @SuppressWarnings("unused")
    public void possibleSize(String name, long size) {
        Sensor sensor = getSensor(name);

        sensor.possibleSize = size;
        // as forecast is depend on by current sensor so it is isolated
        sensor.isolated = true;
    }

    public void start() {
        stepStartTime.set(System.currentTimeMillis());
    }

    public void stop() {
        stepDuration.addAndGet(System.currentTimeMillis() - stepStartTime.get());
    }

    private boolean hasPersonalTimer() {
        return stepDuration.get() != 0;
    }

    public static class Sensor {

        private String name;
        private AtomicInteger sensor;
        private boolean isolated;
        private long possibleSize;
        private int logLength;

        private Sensor(String name) {
            this.name = name;
            sensor = new AtomicInteger();
        }

        private Sensor(Sensor other) {
            this.name = other.name;
            this.sensor = other.sensor;
            this.isolated = other.isolated;
            this.possibleSize = other.possibleSize;
            this.logLength = other.logLength;
        }

        private Sensor newClone() {
            Sensor clone = new Sensor(this);
            clone.sensor = new AtomicInteger(sensor.get());
            return clone;
        }

        private int measure() {
            return sensor.incrementAndGet();
        }

        private int measure(int delta) {
            return sensor.addAndGet(delta);
        }

        private int take() {
            return sensor.get();
        }
    }
}
