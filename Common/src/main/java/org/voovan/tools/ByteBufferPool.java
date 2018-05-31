package org.voovan.tools;

import org.voovan.Global;
import org.voovan.tools.cache.ObjectCachedPool;
import org.voovan.tools.log.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 类文字命名
 *
 * @author: helyho
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class ByteBufferPool {
    public final static ConcurrentSkipListMap<Integer, ObjectCachedPool> STANDER_MEM_BLOCK_BY_SIZE = new ConcurrentSkipListMap<Integer, ObjectCachedPool>();
    public final static ConcurrentHashMap<Long, MemBlockInfo> MEM_BLOCK_INFOS = new ConcurrentHashMap<Long, MemBlockInfo>();


    /**
     * 内存块信息管理策略
     */
    public static class MemBlockInfo{
        private long objectId;
        private int size;
        private ObjectCachedPool objectCachedPool;

        public MemBlockInfo(long objectId, int size, ObjectCachedPool objectCachedPool) {
            this.objectId = objectId;
            this.size = size;
            this.objectCachedPool = objectCachedPool;
        }

        public long getObjectId() {
            return objectId;
        }

        public void setObjectId(long objectId) {
            this.objectId = objectId;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public ObjectCachedPool getObjectCachedPool() {
            return objectCachedPool;
        }

        public void setObjectCachedPool(ObjectCachedPool objectCachedPool) {
            this.objectCachedPool = objectCachedPool;
        }

        public ByteBuffer getByteBuffer(){
            return (ByteBuffer)objectCachedPool.get(objectId);
        }

        public void restitution(long objectId){
            objectCachedPool.restitution(objectId);
        }

        public boolean isCapicityChanged(){
            if(getByteBuffer().capacity() != getSize() * 1024){
                return true;
            } else {
                return false;
            }
        }

    }

    public final static UniqueId uniqueId = new UniqueId(444);
    static {
       init();
    }

    /**
     * 构造一个对象池,用于管理内存块
     * @return
     */
    public static ObjectCachedPool getObjectCachedPool(){
        ObjectCachedPool objectCachedPool = new ObjectCachedPool(30, true);
        objectCachedPool.setDestory((pooledObject)->{
            if(pooledObject instanceof ObjectCachedPool.PooledObject){
                Object objectId = ((ObjectCachedPool.PooledObject) pooledObject).getId();
                Object object = ((ObjectCachedPool.PooledObject) pooledObject).getObject();
                TByteBuffer.release((ByteBuffer)object);
                MEM_BLOCK_INFOS.remove(objectId);
            }

            return null;
        });

        return objectCachedPool;
    }


    /**
     * 初始化
     */
    public static void init() {
        STANDER_MEM_BLOCK_BY_SIZE.put(1, getObjectCachedPool());

        //2-8K
        for(int i=1;i<=3;i++){
            STANDER_MEM_BLOCK_BY_SIZE.put(i*2, getObjectCachedPool());
        }

        //8-256k
        for(int i=1;i<=32;i++){
            STANDER_MEM_BLOCK_BY_SIZE.put(i*8, getObjectCachedPool());
        }

        //256 - 16384k
        for(int i=9;i<15;i++){
            int standerSize = (int)Math.pow(2, (double) i);

            STANDER_MEM_BLOCK_BY_SIZE.put(standerSize, getObjectCachedPool());
        }

        //16384 - Max k
        STANDER_MEM_BLOCK_BY_SIZE.put(-1, getObjectCachedPool());
    }

    /**
     * 获取可用的标准的内存区块大小
     * @param reqireSize
     * @return
     */
    public static int getStanderBlockSize(int reqireSize){
        int kbSize = reqireSize/1024;
        kbSize += reqireSize%1024 > 0 ? 1 : 0;
        try {
            return STANDER_MEM_BLOCK_BY_SIZE.tailMap(kbSize).firstKey();
        } catch (NoSuchElementException e){
            return 0;
        }
    }

    /**
     * 获取接入的实际对象
     * @param objectId 内存块的缓存 id
     * @return ByteBuffer 对象
     */
    public static ByteBuffer getByteBuffer(Object objectId){
        return (ByteBuffer)MEM_BLOCK_INFOS.get(objectId).getByteBuffer();
    }

    /**
     * 获取接入对象的 id
     * @param reqireSize 借出的内存块的缓存 id
     * @return 内存块的缓存 id
     */
    public static Object borrow(int reqireSize){
        int standerBlockSize = getStanderBlockSize(reqireSize);
        if(standerBlockSize>0) {
            ObjectCachedPool objectCachedPool = STANDER_MEM_BLOCK_BY_SIZE.get(standerBlockSize);
            synchronized (objectCachedPool) {
                Object borrowObjectId = objectCachedPool.borrow();
                if (borrowObjectId != null) {
                    return borrowObjectId;
                } else {
                    //分配内存地址
                    ByteBuffer newByteBuffer = TByteBuffer.allocateManualReleaseBuffer(standerBlockSize * 1024);
                    //添加并获取对象 ID
                    long objectId = (Long)objectCachedPool.add(uniqueId.nextInt(), newByteBuffer);
                    //增加块信息进行保存
                    MEM_BLOCK_INFOS.put(objectId, new MemBlockInfo(objectId, standerBlockSize, objectCachedPool));
                    return objectCachedPool.borrow();
                }
            }
        } else {
            ObjectCachedPool objectCachedPool = STANDER_MEM_BLOCK_BY_SIZE.get(-1);
            synchronized (objectCachedPool) {
                Object borrowObjectId = objectCachedPool.borrow();
                if (borrowObjectId != null) {
                    return borrowObjectId;
                } else {
                    //分配内存地址
                    ByteBuffer newByteBuffer = TByteBuffer.allocateManualReleaseBuffer(reqireSize);
                    //添加并获取对象 ID
                    long objectId = (Long)objectCachedPool.add(uniqueId.nextInt(), newByteBuffer);
                    //增加块信息进行保存
                    MEM_BLOCK_INFOS.put(objectId, new MemBlockInfo(objectId, -1, objectCachedPool));
                    return objectCachedPool.borrow();
                }
            }
        }
    }

    /**
     * 归还借出的内存块
     * @param objectId 对象 ID
     */
    public static void restitution(Object objectId){
        MemBlockInfo memBlockInfo = MEM_BLOCK_INFOS.get(objectId);
        if(memBlockInfo.getSize() != -1 && memBlockInfo.isCapicityChanged()){
            TByteBuffer.reallocate(memBlockInfo.getByteBuffer(), memBlockInfo.getSize());
        }

        MEM_BLOCK_INFOS.get(objectId).restitution((Long) objectId);
    }

    public static void main(String[] args) {
        ByteBufferPool.init();


        ArrayList<Object> arrayList = new ArrayList<Object>();
        for(int i=0;i<1000;i++){
            Global.getThreadPool().execute(()->{
                int size = 1234;////(int) (Math.random() * 4096);
                Object objectId = ByteBufferPool.borrow(size);
                ByteBuffer byteBuffer = ByteBufferPool.getByteBuffer(objectId);
                String value = uniqueId.nextString();
                byteBuffer.put(value.getBytes());
//                Logger.simple("borrow->" + objectId + " " + size + " " + byteBuffer.capacity());

//                TEnv.sleep((int) (10*Math.random()));

                Logger.simple("restitution->" + objectId + " " + size);
                String result = TByteBuffer.toString(byteBuffer);
//                System.out.println("content: " + value + " " + result + " /" + result.equals(value));
                ByteBufferPool.restitution(objectId);
            });
        }

        TEnv.sleep(3000);

        while(true) {
            TEnv.sleep(1000);
        }
    }
}
