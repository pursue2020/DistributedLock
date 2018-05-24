package redis.optimistic.lock;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * 静态内部类实现单例模式
 * @author 谭昙
 * @version 1.0.0
 * @create 2018-05-23 16:34
 * @since JDK 1.7.0_79
 */
public class MyJedisPool {

    private MyJedisPool() {
        if(SingletonHolder.pool==null){
            throw new RuntimeException("初始化redis连接池出差");
        }

    }

    public static JedisPool getInstance(){
            return SingletonHolder.pool;
    }

    private static class SingletonHolder{

        static JedisPool pool = null;

        static{
            JedisPoolConfig config = new JedisPoolConfig();
            // 设置最大连接数
            config.setMaxTotal(-1);
            // 设置最大空闲数
            config.setMaxIdle(8);
            // 设置最大等待时间
            config.setMaxWaitMillis(1000 * 100);
            // 在borrow一个jedis实例时，是否需要验证，若为true，则所有jedis实例均是可用的
            config.setTestOnBorrow(true);
            pool = new JedisPool(config, "127.0.0.1", 6379, 3000);

        }

    }
}
