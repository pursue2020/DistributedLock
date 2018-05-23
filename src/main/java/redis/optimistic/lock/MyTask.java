package redis.optimistic.lock;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

/**
 * redis乐观锁模拟高并发抢购
 *
 * 悲观锁：比较适合写入操作比较频繁的场景，如果出现大量的读取操作，每次读取的时候都会进行加锁，
 * 这样会增加大量的锁的开销，降低了系统的吞吐量。
 *
 *乐观锁：比较适合读取操作比较频繁的场景，如果出现大量的写入操作，数据发生冲突的可能性就会增大，
 * 为了保证数据的一致性，应用层需要不断的重新获取数据，这样会增加大量的查询操作，降低了系统的吞吐量。
 *
 *总结：两种所各有优缺点，读取频繁使用乐观锁，写入频繁使用悲观锁。
 * @author 谭昙
 * @version 1.0.0
 * @create 2018-05-23 16:24
 * @since JDK 1.7.0_79
 */
public class MyTask extends OptimisticLockTask{

    private CyclicBarrier cyclicBarrier;

    public MyTask(CyclicBarrier cyclicBarrier) {
        this.cyclicBarrier=cyclicBarrier;
    }

    public Map<String,Boolean> call() throws Exception {
        //默认抢购失败
        Boolean b=false;
        Map<String,Boolean> map=new HashMap<String, Boolean>();
        JedisPool jedisPool=null;
        Jedis jedis=null;

        jedisPool=MyJedisPool.getInstance();
        jedis=jedisPool.getResource();

        String userID= null;
        try {
            System.out.println("当前线程"+Thread.currentThread().getName()+"准备就绪了");
            cyclicBarrier.await();
            //开启事务
            jedis.watch(Constant.WATCHKEY);
            String num=jedis.get(Constant.WATCHKEY);
            int number=Integer.parseInt(num);
            userID = UUID.randomUUID().toString();
            if(number<Constant.NUM){
                System.out.println("当前线程"+Thread.currentThread().getName()+"开始抢购.....");
                Transaction transaction=jedis.multi();
                jedis.incr(Constant.WATCHKEY);
                //提交事务，如果执行事务时发现watchkeys值被修改返回null
                List<Object> list= transaction.exec();
                if(list!=null){
                    System.out.println("当前线程"+Thread.currentThread().getName()+"抢购成功");
                    b=true;
                    jedis.sadd("successInfo",userID);
                }else{
                    System.out.println("当前线程"+Thread.currentThread().getName()+"抢购失败");
                    jedis.sadd("failInfo",userID);
                    jedis.sadd("concurrencyFailInfo",userID);
                }
            }else{
                System.out.println("用户："+userID+",抢购商品失败，请下次再来试试。");
                jedis.sadd("failInfo",userID);
                jedis.sadd("filterFailInfo",userID);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
            System.err.println("程序处理异常了");
        } finally{
            if(jedis!=null) {
                jedis.close();
            }
           jedisPool.close();
        }
        map.put(userID,b);
        return map;
    }
}
