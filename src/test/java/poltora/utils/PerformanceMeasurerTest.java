/*
 * Copyright (c) 2020 Oleg Poltoratskii  www.poltora.info
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

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.apache.commons.lang.math.RandomUtils.nextInt;

/**
 * @author Oleg Poltoratskii ( www.poltora.info )
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PerformanceMeasurerTest {

    private static final Logger LOG = Logger.getLogger(PerformanceMeasurerTest.class);

/*
    2021-01-30 16:53:50,321[1-thread-1][INFO ][cms.Cms.folder301()                     ] 00:00:09 00:00:24 28%  r/s: 1;  r/s/i: 1;  success: 16;
    2021-01-30 16:54:05,315[1-thread-1][INFO ][cms.Cms.folder301()                     ] 00:00:24 00:00:07 75%  r/s: 1(0);  r/s/i: 1(0);  success: 43(+27);
    2021-01-30 16:54:20,316[1-thread-1][INFO ][cms.Cms.folder301()                     ] 00:00:39 00:00:00 101% r/s: 1(0);  r/s/i: 1(0);  success: 98% 57(93% +14);  error: 2% 1;  sum: 58(+15);//todo 101%
*/

    @Test
    public void progressExample() throws Exception {

        int alreadyKnownSize = 500;

        PerformanceMeasurer.getByMethodName().possibleSize(alreadyKnownSize);

        for (int i = 1; i <= alreadyKnownSize; i++) {

            int rnd = nextInt(3);

            if (rnd == 0) {
                PerformanceMeasurer.getByMethodName().success();
            } else if (rnd == 1) {
                PerformanceMeasurer.getByMethodName().fail();
            } else if (rnd == 2) {
                PerformanceMeasurer.getByMethodName().error();
            }

            // internal work
            Thread.sleep(nextInt(i));
        }
    }

    @Test
    public void progressByIsolated() throws Exception {

        int alreadyKnownSize = 5;

        PerformanceMeasurer.getByMethodName().possibleSize("progress", alreadyKnownSize);

        for (int p = 1; p <= alreadyKnownSize; p++) {
            for (int i = 201; i <= 300; i++) {

                int rnd = nextInt(3);

                if (rnd == 0) {
                    PerformanceMeasurer.getByMethodName().success();
                } else if (rnd == 1) {
                    PerformanceMeasurer.getByMethodName().fail();
                } else if (rnd == 2) {
                    PerformanceMeasurer.getByMethodName().error();
                }

                // internal work
                Thread.sleep(nextInt(i));
            }
            PerformanceMeasurer.getByMethodName().measure("progress");
        }
    }

    @Test
    public void httpStatusCodeExample() throws Exception {

        for (int i = 1; i <= 500; i++) {

            int httpStatusCode = 100 + nextInt(500);

            if (httpStatusCode >= 100 && httpStatusCode < 200) {
                PerformanceMeasurer.getByMethodName().measure("Informational");
            } else if (httpStatusCode >= 200 && httpStatusCode < 300) {
                PerformanceMeasurer.getByMethodName().measure("Success");
            } else if (httpStatusCode >= 300 && httpStatusCode < 400) {
                PerformanceMeasurer.getByMethodName().measure("Redirection");
            } else if (httpStatusCode >= 400 && httpStatusCode < 500) {
                PerformanceMeasurer.getByMethodName().measure("Client Error");
            } else if (httpStatusCode >= 500 && httpStatusCode < 600) {
                PerformanceMeasurer.getByMethodName().measure("Server Error");
            }

            // internal work
            Thread.sleep(nextInt(i));
        }
    }

    @Test
    public void httpStatusCodeExactThroughput() throws Exception {

        for (int i = 1; i <= 500; i++) {

            // borders - independent measure
            PerformanceMeasurer.getByMethodName().start();


            int httpStatusCode = 100 + nextInt(500);

            if (httpStatusCode >= 100 && httpStatusCode < 200) {
                PerformanceMeasurer.getByMethodName().measure("Informational");
            } else if (httpStatusCode >= 200 && httpStatusCode < 300) {
                PerformanceMeasurer.getByMethodName().measure("Success");
            } else if (httpStatusCode >= 300 && httpStatusCode < 400) {
                PerformanceMeasurer.getByMethodName().measure("Redirection");
            } else if (httpStatusCode >= 400 && httpStatusCode < 500) {
                PerformanceMeasurer.getByMethodName().measure("Client Error");
            } else if (httpStatusCode >= 500 && httpStatusCode < 600) {
                PerformanceMeasurer.getByMethodName().measure("Server Error");
            }

            int timeOut = nextInt(i);
            int work = timeOut / 10;
            int externalWork = timeOut * 9 / 10;

            // internal work
            Thread.sleep(work);


            // borders - independent measure
            PerformanceMeasurer.getByMethodName().stop();


            // external work
            Thread.sleep(externalWork);
        }
    }


    @Test
    public void isolated() throws Exception {

        PerformanceMeasurer.getByMethodName().setIsolated("isolated");

        for (int i = 1; i <= 500; i++) {

            int rnd = nextInt(4);

            if (rnd == 0) {
                PerformanceMeasurer.getByMethodName().success();
            } else if (rnd == 1) {
                PerformanceMeasurer.getByMethodName().fail();
            } else if (rnd == 2) {
                PerformanceMeasurer.getByMethodName().error();
            } else if (rnd == 3) {
                PerformanceMeasurer.getByMethodName().measure("isolated");
            }

            // internal work
            Thread.sleep(nextInt(i));
        }
    }

    @Test
    public void mute() throws Exception {

        for (int i = 1; i <= 400; i++) {

            int rnd = nextInt(3);

            if (rnd == 0) {
                PerformanceMeasurer.get("test-1").success();
            } else if (rnd == 1) {
                PerformanceMeasurer.get("test-1").fail();
            } else if (rnd == 2) {
                PerformanceMeasurer.get("test-1").error();
            }

            rnd = nextInt(3);

            if (rnd == 0) {
                PerformanceMeasurer.get("test-2").success();
            } else if (rnd == 1) {
                PerformanceMeasurer.get("test-2").fail();
            } else if (rnd == 2) {
                PerformanceMeasurer.get("test-2").error();
            }

            // internal work
            Thread.sleep(nextInt(i));
        }


        for (int i = 1; i <= 400; i++) {

            int rnd = nextInt(3);

            if (rnd == 0) {
                PerformanceMeasurer.get("test-1").success();
            } else if (rnd == 1) {
                PerformanceMeasurer.get("test-1").fail();
            } else if (rnd == 2) {
                PerformanceMeasurer.get("test-1").error();
            }

            // internal work
            Thread.sleep(nextInt(i));
        }


        for (int i = 1; i <= 400; i++) {

            int rnd = nextInt(3);

            if (rnd == 0) {
                PerformanceMeasurer.get("test-1").success();
            } else if (rnd == 1) {
                PerformanceMeasurer.get("test-1").fail();
            } else if (rnd == 2) {
                PerformanceMeasurer.get("test-1").error();
            }

            rnd = nextInt(3);

            if (rnd == 0) {
                PerformanceMeasurer.get("test-2").success();
            } else if (rnd == 1) {
                PerformanceMeasurer.get("test-2").fail();
            } else if (rnd == 2) {
                PerformanceMeasurer.get("test-2").error();
            }

            // internal work
            Thread.sleep(nextInt(i));
        }
    }

    //    @Test
    public void cleanup() throws Exception {

        for (int i = 1; i <= 500; i++) {

            int rnd = nextInt(3);

            if (rnd == 0) {
                PerformanceMeasurer.getByMethodName().success();
            } else if (rnd == 1) {
                PerformanceMeasurer.getByMethodName().fail();
            } else if (rnd == 2) {
                PerformanceMeasurer.getByMethodName().error();
            }

            // internal work
            Thread.sleep(nextInt(i));
        }

        LOG.info("simulating long life process");
        Thread.sleep(24 * 60 * 60 * 1000);
        LOG.info("finish");
    }

    @Test
    public void getByNothing() throws Exception {
        PerformanceMeasurer.get().success();
    }

    @Test
    public void getByName() throws Exception {
        PerformanceMeasurer.get("test-string").success(); //log4j must know about "logger"
    }


    @Test
    public void getByClass() throws Exception {
        PerformanceMeasurer.get(PerformanceMeasurerTest.class).measure("getByClass");
    }

    @Test
    public void getByClass1() throws Exception {
        PerformanceMeasurer.get(PerformanceMeasurer.class).measure("getByClass");
    }

    @Test
    public void getByClassThis() throws Exception {
        PerformanceMeasurer.get(this.getClass()).measure("getByClassThis");
    }

    @Test
    public void getByMethod() throws Exception {
        PerformanceMeasurer.getByMethodName().success();
    }

    @Test
    public void getByMethodTwice() throws Exception {
        PerformanceMeasurer.getByMethodName().measureByMethodName();
    }

    @Test
    public void measureByClass() throws Exception {
        PerformanceMeasurer.getByMethodName().measureByClassName();
    }

    @Test
    public void setProirity() throws Exception {
        // log [DEBUG]
        PerformanceMeasurer.getByMethodName().setPriority(Priority.DEBUG).success();
    }
}

