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
import org.apache.log4j.Priority;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

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
    private static ScheduledExecutorService scheduler;
    private static int time = 15;
    private static TimeUnit timeUnit = TimeUnit.SECONDS;

    private Logger logger;
    private Priority priority;
    private StringBuffer log;

    private String name;
    private Map<String, Sensor> sensors;
    private int possibleSize;
    private long startTime;
    private ThreadLocal<Long> stepStartTime;
    private AtomicLong stepDuration;

    private long currentTime;

    private Sensor summarySensor;
    private Sensor throughputSensor;
    private Sensor throughputMomentSensor;

    // updated state
    private long duration;
    private float percent;
    private long leftTime;

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
            PerformanceMeasurer measurer = entry.getValue();

            if (measurer.isUpdated()) {

                measurer.makeSummary();

                measurer.logger.log(
                        measurer.priority,
                        measurer.log()
                );

                measurer.snapshot();
            }
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
        this.priority = Priority.INFO;

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


        // outside sensor list
        summarySensor = Sensor.getInstance("sum");
        throughputSensor = Sensor.getInstance("r/s");
        throughputMomentSensor = Sensor.getInstance("r/s/i");
    }

    @SuppressWarnings("Convert2streamapi")
    private void snapshot() {

        for (Sensor sensor : sensors.values()) {
            sensor.newClone();
        }


        summarySensor.newClone();
        throughputSensor.newClone();
        throughputMomentSensor.newClone();
    }


    @SuppressWarnings("Convert2streamapi")
    private void makeSummary() {

        currentTime = System.currentTimeMillis();


        if (hasPersonalTimer()) {
            duration = stepDuration.get();
        } else {
            duration = currentTime - startTime;
        }
        if (duration == 0) duration = 1;


        summarySensor.reset(); //history
        for (Sensor sensor : sensors.values()) {

            if (!sensor.isolated) {
                summarySensor.measure(sensor.take());
//                summarySensor.measure(sensor.take() - sensor.history.take());
            }
        }


        throughputSensor.reset();
        throughputSensor.measure((int) ((summarySensor.take() * 1000) / duration));


        throughputMomentSensor.reset();
        throughputMomentSensor.measure(
                (int) (((summarySensor.take() - summarySensor.history.take()) * 1000) / TimeUnit.MILLISECONDS.convert(time, timeUnit))
        );


        // percent & left time
        long count = 0;
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

        if (count != 0) {
            percent = (float) count * 100 / size;
            leftTime = (((long) (duration / percent) * 100)) - duration;
        }
    }


    private int startedSensors() {
        int number = 0;
        for (Sensor sensor : sensors.values()) {
            if (sensor.isStarted()) {
                number++;
            }
        }
        return number;
    }

    private int startedCommonSensors() {
        int number = 0;
        for (Sensor sensor : sensors.values()) {
            if (!sensor.isolated && sensor.isStarted()) {
                number++;
            }
        }
        return number;
    }

    private int updatedSensors() {
        int number = 0;
        for (Sensor sensor : sensors.values()) {

            if (sensor.take() != sensor.history.take()) {
                number++;
            }
        }
        return number;
    }

    private boolean isUpdated() {
        for (Sensor sensor : sensors.values()) {
            if (sensor.take() != sensor.history.take()) {
                return true;
            }
        }

        return false;
    }

    private boolean isForecastCompleted() {

        if (possibleSize != 0) {
            // all sensors forecast
            return summarySensor.take() >= possibleSize;
        } else {
            // exact sensor forecast
            for (Sensor sensor : sensors.values()) {
                if (sensor.possibleSize != 0) {
                    return sensor.take() >= sensor.possibleSize;
                }
            }
            return false;
        }
    }

    private boolean hasLogHistory() {
        return summarySensor.history.isStarted();
    }

    private boolean isLogAtOnce() {
        return isForecastCompleted() && !hasLogHistory();
    }


    private String log() {
        log = new StringBuffer();


        if (hasPersonalTimer())
            log.append("(personal) ");

        logValue(DurationFormatUtils.formatDuration(duration, "HH:mm:ss"));

        logForecast();

        logThroughout();

        logThroughoutMoment();

        logCommon();

        logIsolated();


        return log.toString();
    }

    private void logCommon() {
        if (startedCommonSensors() > 1) {
            logSeveralCommon();
        } else {
            logOneCommon();
        }
    }

    @SuppressWarnings("Convert2streamapi")
    private void logOneCommon() {
        for (Sensor sensor : sensors.values()) {
            if (!sensor.isolated) {
                log(sensor);
            }
        }
    }

    @SuppressWarnings("Convert2streamapi")
    private void logSeveralCommon() {
        for (Sensor sensor : sensors.values()) {
            if (!sensor.isolated) {
                logPercent(sensor);
            }
        }

        log(summarySensor);
    }

    @SuppressWarnings("Convert2streamapi")
    private void logIsolated() {
        for (Sensor sensor : sensors.values()) {
            if (sensor.isolated) {
                log(sensor);
            }
        }
    }

    private void logForecast() {

        if (percent == 0 && leftTime == 0) {
            logValue("   ∞    ");
        } else if (percent == 100) {
            if (hasLogHistory()) {
                logValue("   .    ");
            }
        } else {
            logValue(DurationFormatUtils.formatDuration(leftTime, "HH:mm:ss"));
        }

        if (percent != 100 || hasLogHistory()) {
            logValue(4, (int) percent, "%");
        }
    }

    private void logThroughout() {

        if (summarySensor.isStarted()) { //except isolated
            log(throughputSensor);
        }
    }


    private void logThroughoutMoment() {

        if (!hasPersonalTimer() && summarySensor.isStarted() && !isLogAtOnce()) {
            log(throughputMomentSensor);
        }
    }


    private void logPercent(Sensor sensor) {
        long delta = sensor.take() - sensor.history.take();
        float percent = (float) sensor.take() * 100 / summarySensor.take();
        long deltaSummary = summarySensor.take() - summarySensor.history.take();
        float deltaPercent = (float) delta * 100 / deltaSummary;

        int logLength;
        logLength = logValue(
                sensor.history.logLength,
                sensor.name,
                sensor.take(),
                percent,
                delta,
                deltaPercent
        );
        sensor.logLength = logLength;
    }

    private void log(Sensor sensor) {
        int logLength;
        logLength = logValue(
                sensor.history.logLength,
                sensor.name,
                sensor.take(),
                null,
                sensor.take() - sensor.history.take(),
                null
        );

        sensor.logLength = logLength;
    }


    private int logValue(int logLength, String name, long value, Float percentage, long delta, Float deltaPercentage) {
        DecimalFormat val = new DecimalFormat("0");

        log
                .append(name)
                .append(": ")
        ;

        int length = log.length();


        // %
        if (percentage != null) {
            log
                    .append(val.format(percentage))
                    .append("%")
            ;
            log.append(" ");
        }

        // val
        log.append(String.valueOf(value));

        if (value != delta) {
            log.append("(");

            if (deltaPercentage != null) {
                log
                        .append(val.format(deltaPercentage))
                        .append("% ")
                ;
            }
            log
                    .append(delta > 0 ? "+" : "")
                    .append(String.valueOf(delta))
                    .append(")")
            ;
        }


        log.append(";");

        if (value == delta && !isForecastCompleted()) {
            if (percentage != null) { //not deltaPercentage
                log
                        .append(
                                StringUtils.repeat(" ", String.valueOf(val.format(percentage)).length())
                        )
                        .append(StringUtils.repeat(" ", 2)); // _%
            }
            log
                    .append(
                            StringUtils.repeat(" ", String.valueOf(value).length())
                    )
                    .append(StringUtils.repeat(" ", 3)); // (+)
        }

        int currentLength = log.length() - length;

        if (currentLength < logLength) {
            log.append(StringUtils.repeat(" ", logLength - currentLength));
        }

        if (logLength < currentLength) {
            logLength = currentLength;
        }

        log.append("  ");

        return logLength;
    }

    private int logValue(int logLength, int value, String name) {


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

        log.append(" ");

        return logLength;
    }

    private void logValue(String value) {
        log
                .append(value)
                .append(" ")
        ;
    }


    private Sensor getSensor(String name) {
        return sensors.computeIfAbsent(name, k -> Sensor.getInstance(name));
    }

    public void measure(String name) {
        getSensor(name).measure();
    }

    public void success() {
        getSensor(SUCCESS_NAME).measure();
    }

    public void error() {
        getSensor(ERROR_NAME).measure();
    }

    public void fail() {
        getSensor(FAIL_NAME).measure();
    }

    public void measureByClassName() {
//        String className = Thread.currentThread().getStackTrace()[2].getClass().getSimpleName();
        String className = Thread.currentThread().getStackTrace()[2].getClassName();

        className = className.substring(className.lastIndexOf('.') + 1);

        getSensor(className).measure();
    }

    public void measureByMethodName() {
        String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();

        getSensor(methodName).measure();
    }


    @SuppressWarnings("unused")
    public void measure(String name, int delta) {
        getSensor(name).measure(delta);
    }

    @SuppressWarnings("unused")
    public void success(int delta) {
        getSensor(SUCCESS_NAME).measure(delta);
    }

    @SuppressWarnings("unused")
    public void error(int delta) {
        getSensor(ERROR_NAME).measure(delta);
    }

    @SuppressWarnings("unused")
    public void fail(int delta) {
        getSensor(FAIL_NAME).measure(delta);
    }

    @SuppressWarnings("unused")
    public void measureByClassName(int delta) {
//        String className = Thread.currentThread().getStackTrace()[2].getClass().getSimpleName();
        String className = Thread.currentThread().getStackTrace()[2].getClassName();
        className = className.substring(className.lastIndexOf('.') + 1);

        getSensor(className).measure(delta);
    }

    @SuppressWarnings("unused")
    public void measureByMethodName(int delta) {
        String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();

        getSensor(methodName).measure(delta);
    }

    public void possibleSize(int size) {
        this.possibleSize = size;
    }

    @SuppressWarnings("unused")
    public PerformanceMeasurer setIsolated(String name) {
        Sensor sensor = getSensor(name);
        sensor.isolated = true;
        return this;
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

    public PerformanceMeasurer setPriority(Priority priority) {
        this.priority = priority;
        return this;
    }

    private boolean hasPersonalTimer() {
        return stepDuration.get() != 0;
    }

    public static class Sensor {

        private String name;
        private LongAdder sensor;
        private boolean isolated;
        private long possibleSize;
        private int logLength;
        private Sensor history;

        private static Sensor getInstance(String name) {
            Sensor sensor = new Sensor(name);
            sensor.history = new Sensor("Stub-ancestor-for-sensor");

            return sensor;
        }

        private Sensor(String name) {
            this.name = name;
            sensor = new LongAdder();
        }

        private Sensor(Sensor other) {
            this.name = other.name;
            this.isolated = other.isolated;
            this.possibleSize = other.possibleSize;
            this.logLength = other.logLength;

            //
            this.history = other.history;

            //clone
            this.sensor = other.sensor;
        }

        private Sensor newClone() {
            Sensor clone = new Sensor(this);
            clone.sensor = new LongAdder();
            clone.sensor.add(sensor.sum());

            this.history = clone;
            clone.history = null;

            return clone;
        }

        private void measure() {
            sensor.increment();
        }

        private void measure(long delta) {
            sensor.add(delta);
        }

        private long take() {
            return sensor.sum();
        }

        private void reset() {
            sensor.reset();
        }

        private boolean isStarted() {
            return sensor.sum() != 0;
        }
    }
}
