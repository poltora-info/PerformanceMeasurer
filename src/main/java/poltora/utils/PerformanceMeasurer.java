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

    private static final String summarySensorName = "sum";
    private static final String throughputSensorName = "r/s";
    private static final String throughputMomentSensorName = "r/s/i";

    private Logger logger;
    private Priority priority;
    private StringBuffer log;

    private String name;
    private Map<String, Sensor> sensors;
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
    private Sensor forecastSensor;

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


        boolean flg = false;
        for (PerformanceMeasurer measurer : measurers.values()) {
            if (measurer.isUpdated()) {
                flg = true;
                break;
            }
        }
        if (!flg) return;


        List<PerformanceMeasurer> list = new ArrayList<>();
        for (PerformanceMeasurer measurer : measurers.values()) {
            if (measurer.isUpdated()) {
                list.add(measurer);
            }
        }

        Collections.sort(list, (o1, o2) -> {
            long startTime1 = o1.startTime;
            long startTime2 = o2.startTime;

            return startTime1 > startTime2 ? 1 : startTime1 < startTime2 ? -1 : 0;
        });


        for (PerformanceMeasurer measurer : list) {

            measurer.makeSummary();

            measurer.logger.log(
                    measurer.priority,
                    measurer.log()
            );

            measurer.snapshot();
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
        summarySensor = Sensor.getInstance(summarySensorName, this);
        throughputSensor = Sensor.getInstance(throughputSensorName, this);
        throughputMomentSensor = Sensor.getInstance(throughputMomentSensorName, this);
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

        if (forecastSensor != null) {
            count = forecastSensor.take();
            size = forecastSensor.possibleSize;
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
        return forecastSensor != null && forecastSensor.take() >= forecastSensor.possibleSize;
    }

    private boolean hasLogHistory() {
        return summarySensor.hasLogHistory();
    }

    private boolean isLogAtOnce() {
        return isForecastCompleted() && !hasLogHistory();
    }


    @SuppressWarnings("Convert2streamapi")
    private String log() {
        log = new StringBuffer();


        if (hasPersonalTimer())
            log.append("(personal) ");

        logValue(DurationFormatUtils.formatDuration(duration, "HH:mm:ss"));


        //forecast
        if (forecastSensor != null) {
            if (percent == 0 && leftTime == 0) {
                logValue("   ∞    ");
            } else if (percent == 100) {
                if (hasLogHistory()) {
                    logValue("   .    ");
                }
            } else {
                logValue(DurationFormatUtils.formatDuration(leftTime, "HH:mm:ss"));
            }
        }

        //progress
        if (forecastSensor != null) {
            if (percent != 100 || hasLogHistory()) {
                logValue(4, (int) percent, "%");
            }
        }


        // throughput
        if (summarySensor.isStarted()) { //except isolated
            log.append(throughputSensor.log());
        }


        // throughput Moment
        if (!hasPersonalTimer() && summarySensor.isStarted() && !isLogAtOnce()) {
            log.append(throughputMomentSensor.log());
        }


        //common
        if (startedCommonSensors() > 1) {
            for (Sensor sensor : sensors.values()) {
                if (!sensor.isolated) {
                    log.append(sensor.log(summarySensor));
                }
            }

            log.append(summarySensor.log());
        } else {
            for (Sensor sensor : sensors.values()) {
                if (!sensor.isolated) {
                    log.append(sensor.log(summarySensor));
                }
            }
        }


        //isolated
        for (Sensor sensor : sensors.values()) {
            if (sensor.isolated) {
                log.append(sensor.log());
            }
        }


        return log.toString();
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
        return sensors.computeIfAbsent(name, k -> Sensor.getInstance(name, this));
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
        this.forecastSensor = summarySensor;

        this.summarySensor.possibleSize = size;
    }

    @SuppressWarnings("unused")
    public void possibleSize(String name, long size) {
        Sensor sensor = getSensor(name);

        this.forecastSensor = sensor;
        sensor.possibleSize = size;
        // as forecast is depend on current sensor so it is isolated
        sensor.isolated = true;
    }

    @SuppressWarnings("unused")
    public PerformanceMeasurer setIsolated(String name) {
        Sensor sensor = getSensor(name);
        sensor.isolated = true;
        return this;
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

        private static String logTemplVal = "%s: %s;  "; //sum: 246;
        private static String logTemplDelta = "%s: %s(%s%s);  "; //sum: 342(+96);
        private static String logTemplPerc = "%s: %s%% %s;  "; //success: 33% 81;
        private static String logTemplDeltaPercent = "%s: %s%% %s(%s%% %s%s);  "; //success: 30% 125(28% +22);

        private String name;
        private PerformanceMeasurer measurer;
        private LongAdder sensor;
        private boolean isolated;
        private long possibleSize;
        private int logLength;
        private Sensor history;

        private static Sensor getInstance(String name, PerformanceMeasurer measurer) {
            Sensor sensor = new Sensor(name, measurer);
            sensor.history = new Sensor("Stub-ancestor-for-sensor", measurer);

            return sensor;
        }

        private Sensor(String name, PerformanceMeasurer measurer) {
            this.name = name;
            this.measurer = measurer;
            sensor = new LongAdder();
        }

        private Sensor(Sensor other) {
            this.name = other.name;
            this.measurer = other.measurer;
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
            return take() != 0;
        }

        private boolean hasLogHistory() {
            return history.isStarted();
        }

        private boolean isUpdated() {
            return take() != history.take();
        }


        private String log() {
            return log(null);
        }

        private String log(Sensor summarySensor) {

            String result;


            boolean isAlone = summarySensor != null && this.take() == summarySensor.take();

            boolean isSpecialSensors = name.equals(summarySensorName) || name.equals(throughputSensorName) || name.equals(throughputMomentSensorName);


            long val = take();
            if (isolated || isSpecialSensors || isAlone) {
                if (!hasLogHistory()) {
                    result = String.format(logTemplVal, //sum: 246;
                            name,
                            val
                    );

                    if (!measurer.isForecastCompleted()) {
                        result += StringUtils.repeat(" ", String.valueOf(val).length() + 3); // (+)
                    }
                } else {
                    long delta = val - history.take();

                    result = String.format(logTemplDelta, //sum: 342(+96);
                            name,
                            val,
                            delta > 0 ? "+" : "",
                            delta
                    );
                }
            } else {
                DecimalFormat format = new DecimalFormat("0");

                float percent = (float) val * 100 / summarySensor.take();

                if (!hasLogHistory()) {
                    result = String.format(logTemplPerc, //success: 33% 81;
                            name,
                            format.format(percent),
                            val
                    );

                    if (!measurer.isForecastCompleted()) {
                        result += StringUtils.repeat(" ", String.valueOf(val).length() + 3); // (+)
                        result += StringUtils.repeat(" ", String.valueOf(format.format(percent)).length() + 2);// _%
                    }
                } else {
                    long delta = val - history.take();
                    float deltaPercent = 0;
                    if (delta != 0) {
                        deltaPercent = (float) delta * 100 / (summarySensor.take() - summarySensor.history.take());
                    }

                    result = String.format(logTemplDeltaPercent, //success: 30% 125(28% +22);
                            name,
                            format.format(percent),
                            val,
                            format.format(deltaPercent),
                            delta > 0 ? "+" : "",
                            delta
                    );
                }
            }


            int currentLength = result.length();

            if (currentLength < logLength) {
                result += StringUtils.repeat(" ", logLength - currentLength);
            }
            if (currentLength > logLength) {
                logLength = currentLength;
            }

            return result;
        }

        @Override
        public String toString() {
            return "Sensor{" +
                    "name='" + name + '\'' +
                    ", sensor=" + sensor +
//                    ", isolated=" + isolated +
//                    ", possibleSize=" + possibleSize +
//                    ", logLength=" + logLength +
                    ", history=" + history +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "PerformanceMeasurer{" +
                "name='" + name + '\'' +
                ", sensors=" + sensors +
//                ", startTime=" + startTime +
//                ", stepStartTime=" + stepStartTime +
//                ", stepDuration=" + stepDuration +
//                ", currentTime=" + currentTime +
                ", summarySensor=" + summarySensor +
                ", throughputSensor=" + throughputSensor +
                ", throughputMomentSensor=" + throughputMomentSensor +
                ", duration=" + duration +
                ", percent=" + percent +
                ", leftTime=" + leftTime +
                ", forecastSensor=" + forecastSensor +
                '}';
    }
}
