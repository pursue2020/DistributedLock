package redis.optimistic.lock;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 乐观锁控制
 * @author 谭昙
 * @version 1.0.0
 * @create 2018-05-23 17:22
 * @since JDK 1.7.0_79
 */
public class OptimisticLockTest {

    public static void main(String[] args){
        int number=10000;
        CyclicBarrier cyclicBarrier=new CyclicBarrier(100);
        /**
         * 线程池
         */
        ThreadPoolExecutor executor= (ThreadPoolExecutor) Executors.newFixedThreadPool(100);

        ThreadFactory threadFactory=new BasicThreadFactory.Builder().namingPattern("optimisticlock-thread-pool-%d").daemon(true).build();
        ExecutorService executorService=new ThreadPoolExecutor(10,100,0,
                TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>(1024),threadFactory);

        List<OptimisticLockTask> tasks=new ArrayList<OptimisticLockTask>();
        for(int i=1;i<=number;i++){
            tasks.add(new MyTask(cyclicBarrier));
        }

        try {
            List<Future<Map<String,Boolean>>> list= executor.invokeAll(tasks);
            for(Future<Map<String,Boolean>> f:list){
                Map<String,Boolean> map=f.get();
                for(String key:map.keySet()){
                    System.out.println("用户："+key+",抢购成功=="+map.get(key));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }


    }





}
