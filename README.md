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
[c.d.import.MerchandiseImport] 00:00:12 00:00:14 45%  r/s: 18;       r/s/i: 18;       fail: 36% 82;           success: 33% 75;           error: 31% 72;           sum: 229;        
[c.d.import.MerchandiseImport] 00:00:27 00:00:12 68%  r/s: 12(-6);   r/s/i: 7(-11);   fail: 37% 126(39% +44);  success: 32% 111(32% +36);  error: 31% 106(30% +34);  sum: 343(+114);  
[c.d.import.MerchandiseImport] 00:00:42 00:00:08 84%  r/s: 9(-3);    r/s/i: 5(-2);    fail: 35% 149(30% +23);  success: 34% 141(39% +30);  error: 31% 130(31% +24);  sum: 420(+77);   
[c.d.import.MerchandiseImport] 00:00:57 00:00:01 98%  r/s: 8(-1);    r/s/i: 4(-1);    fail: 37% 179(43% +30);  success: 32% 156(21% +15);  error: 32% 155(36% +25);  sum: 490(+70);   
[c.d.import.MerchandiseImport] 00:01:01    .     100% r/s: 8(0);     r/s/i: 2(-2);    fail: 37% 183(40% +4);   success: 32% 160(40% +4);   error: 31% 157(20% +2);   sum: 500(+10);   
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

    // say measurer about progress by isolated sensor
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
[c.d.import.MerchandiseImport] 00:00:06    ∞     0%   r/s: 8;      r/s/i: 8;      fail: 39% 21;           success: 26% 14;           error: 35% 19;           sum: 54;       shop: 0;      
[c.d.import.MerchandiseImport] 00:00:21 00:01:26 20%  r/s: 7(-1);  r/s/i: 7(-1);  fail: 31% 53(28% +32);  success: 28% 48(29% +34);  error: 41% 69(43% +50);  sum: 170(+116);  shop: 1;      
[c.d.import.MerchandiseImport] 00:00:36 00:00:54 40%  r/s: 7(0);   r/s/i: 7(0);   fail: 33% 94(35% +41);  success: 31% 90(36% +42);  error: 36% 102(28% +33);  sum: 286(+116);  shop: 2(+1);  
[c.d.import.MerchandiseImport] 00:00:51 00:00:12 80%  r/s: 7(0);   r/s/i: 7(0);   fail: 35% 139(39% +45);  success: 30% 122(28% +32);  error: 35% 141(34% +39);  sum: 402(+116);  shop: 4(+2);  
[c.d.import.MerchandiseImport] 00:01:06    .     100% r/s: 7(0);   r/s/i: 6(-1);  fail: 33% 167(29% +28);  success: 31% 156(35% +34);  error: 35% 177(37% +36);  sum: 500(+98);   shop: 5(+1);  
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
[c.d.h.HttpStatusCode] 00:00:13 r/s: 16;       r/s/i: 16;       Redirection: 20% 46;           Server Error: 25% 58;           Client Error: 20% 47;           Informational: 17% 40;           Success: 18% 41;           sum: 232;        
[c.d.h.HttpStatusCode] 00:00:28 r/s: 11(-5);   r/s/i: 6(-10);   Redirection: 20% 67(22% +21);  Server Error: 23% 74(17% +16);  Client Error: 22% 73(27% +26);  Informational: 16% 54(15% +14);  Success: 18% 60(20% +19);  sum: 328(+96);   
[c.d.h.HttpStatusCode] 00:00:43 r/s: 9(-2);    r/s/i: 5(-1);    Redirection: 20% 80(17% +13);  Server Error: 22% 88(19% +14);  Client Error: 22% 88(20% +15);  Informational: 19% 75(28% +21);  Success: 18% 72(16% +12);  sum: 403(+75);   
[c.d.h.HttpStatusCode] 00:00:58 r/s: 8(-1);    r/s/i: 4(-1);    Redirection: 20% 94(19% +14);  Server Error: 21% 100(17% +12);  Client Error: 21% 102(19% +14);  Informational: 19% 92(24% +17);  Success: 18% 87(21% +15);  sum: 475(+72);   
[c.d.h.HttpStatusCode] 00:01:13 r/s: 6(-2);    r/s/i: 1(-3);    Redirection: 20% 99(20% +5);   Server Error: 21% 106(24% +6);   Client Error: 21% 106(16% +4);   Informational: 19% 97(20% +5);   Success: 18% 92(20% +5);   sum: 500(+25);   
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
[c.d.h.HttpStatusCode] (personal) 00:00:01 r/s: 157;        r/s/i: 0;      Redirection: 21% 50;           Server Error: 21% 50;           Informational: 22% 54;           Client Error: 19% 45;           Success: 18% 44;           sum: 243;        
[c.d.h.HttpStatusCode] (personal) 00:00:03 r/s: 111(-46);   r/s/i: 0;      Redirection: 20% 69(19% +19);  Server Error: 21% 72(22% +22);  Informational: 20% 68(14% +14);  Client Error: 21% 72(27% +27);  Success: 18% 61(17% +17);  sum: 342(+99);   
[c.d.h.HttpStatusCode] (personal) 00:00:04 r/s: 90(-21);    r/s/i: 0;      Redirection: 21% 86(22% +17);  Server Error: 21% 88(21% +16);  Informational: 19% 80(16% +12);  Client Error: 21% 88(21% +16);  Success: 18% 77(21% +16);  sum: 419(+77);   
[c.d.h.HttpStatusCode] (personal) 00:00:06 r/s: 81(-9);     r/s/i: 0;      Redirection: 22% 107(28% +21);  Server Error: 21% 104(21% +16);  Informational: 18% 91(15% +11);  Client Error: 21% 105(23% +17);  Success: 18% 87(13% +10);  sum: 494(+75);   
[c.d.h.HttpStatusCode] (personal) 00:00:06 r/s: 80(-1);     r/s/i: 0;      Redirection: 22% 108(17% +1);   Server Error: 21% 106(33% +2);   Informational: 18% 92(17% +1);   Client Error: 21% 106(17% +1);   Success: 18% 88(17% +1);   sum: 500(+6);    
```

## Licensing
PerformanceMeasurer is licensed under the [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)
