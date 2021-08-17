package com.atguigu.gulimall.search.thread;

import java.util.concurrent.*;


public class ThreadTest {
    public static ExecutorService executor  =  Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        System.out.println("main....start....");
//        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//            System.out.println("当前线程：" + Thread.currentThread().getId());
//            int i = 10 / 2;
//            System.out.println("运行结果:" + i);
//        }, executor);

//        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
//            System.out.println("当前线程：" + Thread.currentThread().getId());
//            int i = 10 / 0;
//            System.out.println("运行结果:" + i);
//            return i;
//        }, executor).whenComplete((res,excption)->{ //虽然能得到异常信息，但是没法修改返回数据
//            System.out.println("异步任务成功完成了...结果是:"+res+"；异常信息是"+excption);
//        }).exceptionally(throwable -> {  //可以感知异常，同时返回默认值
//            return 10;
//        }); //成功以后干啥事

//        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
//            System.out.println("当前线程：" + Thread.currentThread().getId());
//            int i = 10 / 4;
//            System.out.println("运行结果:" + i);
//            return i;
//        }, executor).handle((res,thr)->{
//            if (res!=null){
//                return res * 2;
//            }
//            if (thr!=null){  //异常不等于空了，就返回0
//                return 0;
//            }
//            return 0;
//        });

        /**
         * 线程的串行化
         * 1.thenRun:不能获取到上一步的执行结果
         * .thenRunAsync(() -> {
         *             System.out.println("任务2启动了...");
         *         }, executor);
         *
         * 2.thenAcceptAsync  能接受上一步的结果，但是没有返回值
         * .thenAcceptAsync(res->{
         *                  System.out.println("任务2启动了..."+res);
         *         },executor);
         *
         * 3. thenApplyAsync 能获取到上一步的结果 同时也有返回值
         *
//         */
//        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
//            System.out.println("当前线程：" + Thread.currentThread().getId());
//            int i = 10 / 4;
//            System.out.println("运行结果:" + i);
//            return i;
//        }, executor).thenApplyAsync(res -> {
//            System.out.println("任务2启动了..." + res);
//            return "Hello" + res;
//        }, executor);
        //Integer integer = future1.get();

//        /**
//         * 两任务组合
//         */
//        CompletableFuture<Integer> future01 = CompletableFuture.supplyAsync(() -> {
//            System.out.println("任务1线程：" + Thread.currentThread().getId());
//            int i = 10 / 4;
//            System.out.println("任务1结束:" + i);
//            return i;
//        }, executor);
//
//        CompletableFuture<String> future02 = CompletableFuture.supplyAsync(() -> {
//            System.out.println("任务2线程：" + Thread.currentThread().getId());
//            System.out.println("任务2结束:");
//            return "hello";
//        }, executor);

//        future01.runAfterBothAsync(future02,()->{
//            System.out.println("任务3开始" );
//        },executor);

//        future01.thenAcceptBothAsync(future02,(f1,f2)->{
//            System.out.println("任务3开始 之前的结果:"+f1+"-->"+f2 );
//        },executor);
//        CompletableFuture<String> future = future01.thenCombineAsync(future02, (f1, f2) -> {
//            return f1 + ":" + f2 + "->haha";
//        }, executor);
//        future01.runAfterEitherAsync(future02,()->{
//            System.out.println("任务3开始 之前的结果:");
//        },executor);

        CompletableFuture<String> futureImg = CompletableFuture.supplyAsync(() -> {
            System.out.println("查询商品的图片信息");
            return "hello.jpg";
        },executor);
        CompletableFuture<String> futureAttr = CompletableFuture.supplyAsync(() -> {
            System.out.println("查询商品的属性");
            return "黑色+256G";
        },executor);
        CompletableFuture<String> futureDesc = CompletableFuture.supplyAsync(() -> {
            System.out.println("查询商品的介绍");
            return "华为";
        },executor);
       // futureImg.get();futureAttr.get();futureDesc.get();
        //CompletableFuture<Void> allOf = CompletableFuture.allOf(futureImg, futureAttr, futureDesc);
        //System.out.println("main....end..."+futureImg.get()+"=>"+futureAttr.get()+"=>"+futureDesc.get());
        CompletableFuture<Object> anyOf = CompletableFuture.anyOf(futureImg, futureAttr, futureDesc);
        anyOf.get();//等待所有结果完成
        System.out.println("main....end..."+anyOf.get());
    }


    public  void thread(String[] args) throws ExecutionException, InterruptedException {
       System.out.println("main....start....");
//        Thread01 thread01 = new Thread01();
//        thread01.start();//启动线程
//        System.out.println("main....end...");

//        Runable01 runable01 = new Runable01();
//        new Thread(runable01).start();
//        System.out.println("main....end...");

//        FutureTask<Integer> futureTask = new FutureTask<>(new Callable01());
//        new Thread(futureTask).start();
//        Integer integer = futureTask.get();//阻塞等待
       // System.out.println("main....end..."+integer);

        /**
         * 线程池：
         *  给线程池直接提交任务
         *  1.创建线程池的方式
         *      1.1.使用Executors线程池工具类来创建线程池   service.execute(new Runable01());
         *      1.2.使用原生的线程池创建方式
         *
         *
         */
        /**
         * 原生线程池的七大参数解释
         *  int corePoolSize, 核心线程数｛只要线程池不销毁，核心线程数一直在｝，线程池创建好以后就准备就绪的线程数量，就等待来接收异步任务去来执行
         *  int maximumPoolSize, 最大线程数，控制资源并发的
         *  long keepAliveTime, 存活时间，如果当前线程数量大于核心数量，只要线程空闲到一定时间内，就会释放空闲的最大线程数当中的线程
         *  TimeUnit unit,  具体最大线程数的存活时间的时间单位
         *  BlockingQueue<Runnable> workQueue, 阻塞队列 如果任务有很多，就会将目前多的任务放在队列里面，只要有空闲的线程，就会去阻塞队列去拿新的任务
         *  ThreadFactory threadFactory,  线程的创建工厂 默认 也可以自定义
         *  RejectedExecutionHandler handler   拒绝策略，就是处理阻塞队列当中任务已满了，不能再加入其他的任务进来阻塞队列当中了，就进行指定的拒绝策略进行拒绝任务
         *
         *  工作顺序：
         * 1. 线程池创建，准备好核心数量的线程，准备接受任务
         * 2. 核心线程数量若满了，就把新进来的任务放到阻塞队列当中，等到核心线程空闲了就去阻塞队列拿新任务并进行执行
         * 3. 如果阻塞队列满了，会开启指定的最大线程数量进行执行阻塞队列当中的任务，并若在指定的时间内最大线程数空闲了，就会释放资源
         * 4.如果阻塞队列和最大现场数量都满了，那么就会使用指定的拒绝策略，来拒绝接受新进来的任务
         *
         *
         * 线程池的其他方法：
         *  Executors.newCachedThreadPool() //核心数是0，所有都可回收
         *         Executors.newFixedThreadPool() //固定大小，核心数=最大值都不可回收
         *         Executors.newScheduledThreadPool() //定时任务的线程池
         *         Executors.newSingleThreadExecutor() //单线程的线程池，后台从队列里面获取任务，挨个执行
         *
         */
        ThreadPoolExecutor executor = new ThreadPoolExecutor(5,
                                                        200,
                                                            10,
                                                        TimeUnit.SECONDS,
                                                        new LinkedBlockingDeque<>(100000),
                                                        Executors.defaultThreadFactory(),
                                                        new ThreadPoolExecutor.AbortPolicy());
        System.out.println("main....end...");
    }
    public static class Thread01 extends Thread{
        @Override
        public void run() {
            System.out.println("当前线程："+Thread.currentThread().getId());
            int i = 10 / 2;
            System.out.println("运行结果:"+i);
        }
    }
    public static class Runable01 implements Runnable{

        @Override
        public void run() {
            System.out.println("当前线程："+Thread.currentThread().getId());
            int i = 10 / 2;
            System.out.println("运行结果:"+i);
        }
    }

    public static class Callable01 implements Callable<Integer>{

        @Override
        public Integer call() throws Exception {
            System.out.println("当前线程："+Thread.currentThread().getId());
            int i = 10 / 2;
            System.out.println("运行结果:"+i);
            return i;
        }
    }
}
