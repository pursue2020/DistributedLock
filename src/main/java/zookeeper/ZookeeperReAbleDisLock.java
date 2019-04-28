package zookeeper;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * @author tantan
 * @version 1.0.0 2019-04-28 14:14
 * @description 1  重入的实现
 * 对于锁的重入，我们来想这样一个场景。当一个递归方法被sychronized关键字修饰时，
 * 在调用方法时显然没有发生问题，执行线程获取了锁之后仍能连续多次地获得该锁，
 * 也就是说sychronized关键字支持锁的重入。对于ReentrantLock，
 * 虽然没有像sychronized那样隐式地支持重入，但在调用lock()方法时，
 * 已经获取到锁的线程，能够再次调用lock()方法获取锁而不被阻塞。
 *
 * 如果想要实现锁的重入，至少要解决一下两个问题
 *
 * 1、线程再次获取锁：锁需要去识别获取锁的线程是否为当前占据锁的线程，如果是，则再次成功获取。
 * 锁的最终释放：线程重复n次获取了锁，随后在n次释放该锁后，其他线程能够获取该锁。
 *
 * 2、锁的最终释放要求锁对于获取进行计数自增，计数表示当前锁被重复获取的次数，
 * 而锁被释放时，计数自减，当计数等于0时表示锁已经释放
 * @since JDK 1.8.0_172
 */
public class ZookeeperReAbleDisLock implements Lock {

    private ZkClient client;

    private String lockPath;

    private String currentPath;

    private String beforePath;

    // 线程获取锁的次数
    private static volatile int state;
    // 当前获取锁的线程
    private static volatile Thread thread;

    public ZookeeperReAbleDisLock(String lockPath) {
        super();
        this.lockPath = lockPath;

        client = new ZkClient("127.0.0.1:2181");
        client.setZkSerializer(new MyZkSerializer());

        if (!client.exists(lockPath)) {
            try {
                client.createPersistent(lockPath);
            } catch (Exception e) {
            }
        }

    }

    public boolean tryLock() {
        return tryLock(1);
    }

    public boolean tryLock(int acquires) {
        // 获取当前线程
        final Thread currntThread = Thread.currentThread();
        // 获取当前锁的次数
        int state = getState();

        // state == 0 表示没有线程获取锁 进来的线程肯定能获取锁
        if (state == 0) {
            if (compareAndSetState(0, acquires, currntThread)) {
                return true;
            }

            // state != 0 表示线程被获取了 判断是否是当前线程 如果是则 state+1 ,否则返回false
        } else if (currntThread == getThread()) {
            int nextS = getState() + acquires;

            if (nextS < 0)
                throw new Error("Maximum lock count exceeded");
            // 这里不需要cas 原因不解释
            setState(nextS);
            System.out.println(Thread.currentThread().getName() + ":获取重入锁成功,当前获取锁次数: " + getState());
            return true;
        }

        // 获取锁的不是当前线程
        return false;
    }

    public boolean compareAndSetState(int expect, int update, Thread t) {

        if (this.currentPath == null) {
            currentPath = this.client.createEphemeralSequential(lockPath + "/", "1");
        }

        // 获取所有节点
        List<String> children = this.client.getChildren(lockPath);

        // 排序
        Collections.sort(children);

        // 判断当前节点是否为最小节点
        if (getState() == expect && currentPath.equals(lockPath + "/" + children.get(0))) {
            setState(update);
            thread = t;
            System.out.println(Thread.currentThread().getName() + ":获取锁成功,当前获取锁次数: " + getState());
            return true;
        }

        // 取得前一个
        // 得到字节的索引号
        int curIndex = children.indexOf(currentPath.substring(lockPath.length() + 1));
        beforePath = lockPath + "/" + children.get(curIndex - 1);

        return false;
    }

    public final boolean tryRelease(int releases) {
        // 可以判断是否自己获得锁 自己获得锁才能删除
        final Thread currentThread = Thread.currentThread();
        // 获取锁的不是当前线程
        if (currentThread != getThread()) {
            // throw new IllegalMonitorStateException();
            return false;
        }

        // 释放锁的次数
        int nextS = getState() - releases;
        boolean free = false;
        if (nextS == 0) {
            free = true;
            setThread(null);
            // 删除zk节点
            client.delete(currentPath);
            System.out.println(Thread.currentThread().getName() + ": 所有锁释放成功:删除zk节点...");
        }

        setState(nextS);

        if (!free)
            System.out.println(Thread.currentThread().getName() + ": 释放重入锁成功: 剩余锁次数：" + getState());

        return free;
    }


    public void lock() {
        if (!tryLock()) {
            // 没有获得锁，阻塞自己
            waitForLock();
            // 再次尝试加锁
            lock();
        }
    }

    private void waitForLock() {
        final CountDownLatch cdl = new CountDownLatch(1);

        IZkDataListener listener = new IZkDataListener() {

            public void handleDataChange(String arg0, Object arg1) throws Exception {

            }

            public void handleDataDeleted(String arg0) throws Exception {
                System.out.println("节点被删除了，开始抢锁...");
                cdl.countDown();
            }

        };
        // 完成watcher注册
        this.client.subscribeDataChanges(beforePath, listener);

        // 阻塞自己
        if (this.client.exists(beforePath)) {
            try {
                cdl.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // 取消注册
        this.client.unsubscribeDataChanges(beforePath, listener);
    }


    public void unlock() {
        // 可以判断是否自己获得锁 自己获得锁才能删除
        // client.delete(currentPath);
        tryRelease(1);

    }

    public static int getState() {
        return state;
    }

    public static void setState(int state) {
        ZookeeperReAbleDisLock.state = state;
    }

    public static Thread getThread() {
        return thread;
    }

    public static void setThread(Thread thread) {
        ZookeeperReAbleDisLock.thread = thread;
    }


    public void lockInterruptibly() throws InterruptedException {
    }


    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        // TODO Auto-generated method stub
        return false;
    }


    public Condition newCondition() {
        // TODO Auto-generated method stub
        return null;
    }

    class MyZkSerializer implements ZkSerializer {

        public Object deserialize(byte[] bytes) throws ZkMarshallingError {
            try {
                return new String(bytes, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                throw new ZkMarshallingError(e);
            }
        }

        public byte[] serialize(Object obj) throws ZkMarshallingError {
            try {
                return String.valueOf(obj).getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                throw new ZkMarshallingError(e);
            }
        }

    }

}
