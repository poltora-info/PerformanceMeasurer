# PerformanceMeasurer

*Open source Java library for getting information about workflow of Java application.*


## Contents

- Description
- Use cases
  - Progress measuring
  - Progress measuring by isolated criterion
  - HTTP status code measuring
  - Throughput of exact code
- Licensing

## Description

Tool logs time, throughput per second, progress and forecast of workflow, **sensors** data (measured parameters) for each **measurer** (analyzed entity) with configurable frequency (default 15 seconds). Each **sensor** indicates measured value, changes after previous logging, percent among other **sensors** of current **measurer**.

## Use cases

### Progress measuring

There are several pre-defined sensor's names such as `success`, `fail` and `error`. But you can use any string for any sensors. For example `.success();` or `.measure("success");`

#### Call

```java
// get list of merchandise
List<Merchandise> merchandiseList = getMerchandiseList();

// set possible workload
PerformanceMeasurer.get().possibleSize(merchandiseList.size());

for (Merchandise merchandise : merchandiseList) {
    try {

        // checking data
        boolean isGood = checkData(merchandise);

        if (!isGood) {
            // say measurer about user's mistake
            PerformanceMeasurer.get().fail();
        } else {
            // if ok then some work...
            // ...

            // say measurer about success
            PerformanceMeasurer.get().success();
        }

    } catch (Exception e) {
        // say measurer about serious failure
        PerformanceMeasurer.get().error();

        LOG.error(merchandise.toString() + e, e);
    }
}

```

#### Result

```shell
c.d.import.MerchandiseImport 00:00:15 00:00:15 49%   r/s: 16;  fail: 35% 86;  success: 34% 83;  error: 31% 77;  sum: 246;
c.d.import.MerchandiseImport 00:00:29 00:00:12 70%   r/s: 11(-5);  fail: 34% 121(+35);  success: 34% 120(+37);  error: 31% 110(+33);  sum: 351(+105);
c.d.import.MerchandiseImport 00:00:44 00:00:07 86%   r/s: 9(-2);   fail: 35% 151(+30);  success: 34% 145(+25);  error: 31% 135(+25);  sum: 431(+80); 
c.d.import.MerchandiseImport 00:00:59 00:00:00 99%   r/s: 8(-1);   fail: 35% 175(+24);  success: 34% 166(+21);  error: 31% 154(+19);  sum: 495(+64); 
c.d.import.MerchandiseImport 00:01:01    .     100%  r/s: 8(0);    fail: 36% 178(+3);   success: 33% 167(+1);   error: 31% 155(+1);   sum: 500(+5);  
```

### Progress measuring by isolated criterion

In that case if it is unknown in advance maximum value of important criterion but it is possible to track progress by separated criterion you can use it as **isolated sensor** in `possibleSize(isolatedSensor, size)`.   

For example to calculate number of goods during import from shops we know only number of shops. You can use “shop” as isolated sensor.     


#### Call

```java
// get list of shops
List<Shop> shopList = getShops();

// set possible workload of shops
PerformanceMeasurer.get().possibleSize("shop", shopList.size());

for (Shop shop : shopList) {
    // get list of merchandise in shop
    List<Merchandise> merchandiseList = shop.getMerchandise();

    // say measurer abut progress by isolated sensor
    PerformanceMeasurer.get().measure("shop");

    for (Merchandise merchandise : merchandiseList) {
        try {
            // checking data
            boolean isGood = checkData(merchandise);

            if (!isGood) {
                // say measurer about user's mistake
                PerformanceMeasurer.get().fail();
            } else {
                // if ok then some work...

                // say measurer about success
                PerformanceMeasurer.get().success();
            }
        } catch (Exception e) {
            // say measurer about serious failure
            PerformanceMeasurer.get().error();
            LOG.error(merchandise.toString() + e, e);
        }
    }
}
```

#### Result

```shell
c.d.import.MerchandiseImport 00:00:03    ∞     0%    r/s: 8;  fail: 33% 10;  success: 30% 9;  error: 37% 11;  sum: 30;  shop: 0;
c.d.import.MerchandiseImport 00:00:18 00:01:14 20%   r/s: 8(0);  fail: 36% 54(+44);  success: 37% 55(+46);  error: 27% 41(+30);  sum: 150(+120);  shop: 1;
c.d.import.MerchandiseImport 00:00:33 00:00:50 40%   r/s: 8(0);  fail: 35% 95(+41);  success: 35% 96(+41);  error: 30% 81(+40);  sum: 272(+122);  shop: 2(+1);
c.d.import.MerchandiseImport 00:00:48 00:00:32 60%   r/s: 7(-1);  fail: 37% 143(+48);  success: 34% 130(+34);  error: 29% 109(+28);  sum: 382(+110);  shop: 3(+1);
c.d.import.MerchandiseImport 00:01:03 00:00:15 80%   r/s: 7(0);   fail: 36% 179(+36);  success: 33% 164(+34);  error: 31% 154(+45);  sum: 497(+115);  shop: 4(+1);
c.d.import.MerchandiseImport 00:01:18    .     100%  r/s: 6(-1);  fail: 36% 180(+1);   success: 33% 165(+1);   error: 31% 155(+1);   sum: 500(+3);    shop: 5(+1);

```

### HTTP status code measuring

For example, we have a web crawler which browses web-sites, and we need a statistic of HTTP response status codes - 1xx/2xx/3xx/4xx/5xx.

#### Call

```java
if (httpStatusCode >= 100 && httpStatusCode < 200) {
    PerformanceMeasurer.get().measure("Informational");
} else if (httpStatusCode >= 200 && httpStatusCode < 300) {
    PerformanceMeasurer.get().measure("Success");
} else if (httpStatusCode >= 300 && httpStatusCode < 400) {
    PerformanceMeasurer.get().measure("Redirection");
} else if (httpStatusCode >= 400 && httpStatusCode < 500) {
    PerformanceMeasurer.get().measure("Client Error");
} else if (httpStatusCode >= 500 && httpStatusCode < 600) {
    PerformanceMeasurer.get().measure("Server Error");
}
```

#### Result

```shell
c.d.h.HttpStatusCode 00:00:15  r/s: 15;  Redirection: 16% 38;  Server Error: 21% 51;  Client Error: 19% 45;  Informational: 18% 43;  Success: 26% 61;  sum: 238;
c.d.h.HttpStatusCode 00:00:29  r/s: 11(-4);  Redirection: 16% 54(+16);  Server Error: 23% 77(+26);  Client Error: 21% 71(+26);  Informational: 18% 61(+18);  Success: 23% 78(+17);  sum: 341(+103);
c.d.h.HttpStatusCode 00:00:44  r/s: 9(-2);   Redirection: 18% 73(+19);  Server Error: 21% 86(+9);   Client Error: 22% 90(+19);  Informational: 18% 73(+12);  Success: 23% 95(+17);  sum: 417(+76); 
c.d.h.HttpStatusCode 00:00:59  r/s: 7(-2);   Redirection: 19% 89(+16);  Server Error: 20% 95(+9);   Client Error: 22% 103(+13);  Informational: 17% 83(+10);  Success: 22% 106(+11);  sum: 476(+59); 
c.d.h.HttpStatusCode 00:01:06  r/s: 7(0);    Redirection: 19% 96(+7);   Server Error: 20% 99(+4);   Client Error: 22% 111(+8);   Informational: 17% 86(+3);   Success: 22% 108(+2);   sum: 500(+24); 
```

### Throughput of exact code

Usually, software system consists of big number modules, each of them can be processed by separated **measurer** (with it’s **sensors**). By default, throughput any module is calculated for whole execution period of **whole** system.

But sometimes it is necessary to calculate throughput of certain module (block of code) separately. In this case borders of code can be marked by call `.start()` and `.stop()`

#### Example

Let’s modify example above:


```java
private boolean downloadAndSavePage(String url) {

    // borders - independent measure
    PerformanceMeasurer.get().start();


    // internal long work...
    int httpStatusCode = downloadPage(url);
    

    if (httpStatusCode >= 100 && httpStatusCode < 200) {
        PerformanceMeasurer.get().measure("Informational");
    } else if (httpStatusCode >= 200 && httpStatusCode < 300) {
        PerformanceMeasurer.get().measure("Success");
    } else if (httpStatusCode >= 300 && httpStatusCode < 400) {
        PerformanceMeasurer.get().measure("Redirection");
    } else if (httpStatusCode >= 400 && httpStatusCode < 500) {
        PerformanceMeasurer.get().measure("Client Error");
    } else if (httpStatusCode >= 500 && httpStatusCode < 600) {
        PerformanceMeasurer.get().measure("Server Error");
    }


    // borders - independent measure
    PerformanceMeasurer.get().stop();

    return true;
}

```

#### Result

```bash
c.d.h.HttpStatusCode (personal) 00:00:01  r/s: 159;  Redirection: 20% 51;  Server Error: 24% 61;  Client Error: 19% 48;  Informational: 17% 42;  Success: 19% 47;  sum: 249;
c.d.h.HttpStatusCode (personal) 00:00:03  r/s: 112(-47);  Redirection: 21% 73(+22);  Server Error: 25% 88(+27);  Client Error: 18% 63(+15);  Informational: 18% 62(+20);  Success: 18% 64(+17);  sum: 350(+101);
c.d.h.HttpStatusCode (personal) 00:00:04  r/s: 92(-20);   Redirection: 20% 86(+13);  Server Error: 24% 104(+16);  Client Error: 19% 82(+19);  Informational: 19% 79(+17);  Success: 18% 75(+11);  sum: 426(+76); 
c.d.h.HttpStatusCode (personal) 00:00:06  r/s: 78(-14);   Redirection: 20% 97(+11);  Server Error: 24% 114(+10);  Client Error: 20% 98(+16);  Informational: 19% 90(+11);  Success: 18% 85(+10);  sum: 484(+58); 
c.d.h.HttpStatusCode (personal) 00:00:06  r/s: 78(0);     Redirection: 19% 97(0);    Server Error: 23% 116(+2);   Client Error: 21% 103(+5);  Informational: 19% 95(+5);   Success: 18% 89(+4);   sum: 500(+16); 
```

## Licensing
PerformanceMeasurer is licensed under the [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)
