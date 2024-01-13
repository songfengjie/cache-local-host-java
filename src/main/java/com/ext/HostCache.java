package com.ext;

import io.netty.util.internal.NativeLibraryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扩展缓存，使用主机内存存储缓存数据
 */
public class HostCache {

    private static final Logger log = LoggerFactory.getLogger(HostCache.class);

    public static final HostCache INSTANCE;

    private static HostCacheSerialEnum serial = HostCacheSerialEnum.protostuff;

    /**
     * 扩展是否可用
     */
    private static final boolean USABLE;

    static {
        INSTANCE = new HostCache();
        boolean loaded = false;
        try {
            //liblink.dylib是arm架构的  linkmac是x86
            NativeLibraryLoader.loadFirstAvailable(HostCache.class.getClassLoader(), "link", "linkmac");
            loaded = true;
        } catch (Throwable ignored) {
            log.warn("<hostCache>扩展缓存ExtCache不可用，没有加载到扩展库");
        } finally {
            USABLE = loaded;
        }
    }

    public static HostCacheSerialEnum getSerial() {
        return serial;
    }

    public static void setSerial(HostCacheSerialEnum serial) {
        HostCache.serial = serial;
    }

    /**
     * 允容器使用的最大内存
     * @param maxUsableBytes 最大字节数
     */
    public static native void containerMaxUseMemSize(long maxUsableBytes);

    /**
     * 设置缓存
     * @param key 缓存key
     * @param data 缓存数据
     */
    public static native void put(String key, byte[] data);

    /**
     * 根据key获取缓存
     * @param key 缓存的key
     * @return 缓存值
     */
    public static native byte[] get(String key);

    /**
     * 删除某个缓存
     * @param key 要删除的缓存key
     */
    public static native void delKey(String key);

    /**
     * 清空缓存
     */
    public static native void clear();

    /**
     * 设置缓存
     * @param key 缓存key
     * @param data 数据
     * @return 是否成功
     */
    public boolean putObj(String key, Object data) {

        if (data == null) {
            return false;
        }
        byte[] bytes;
        switch (serial) {
            case kryo:
                bytes = KryoInnerTool.writeToByteArray(data);
                break;
            case protostuff:
            default:
                bytes = ProtostuffInnerTool.serialize(data);
        }
        HostCache.put(key, bytes);
        return true;
    }

    /**
     * 读取缓存
     * @param key 缓存key
     * @param type 读取的类型
     * @return 数据
     */
    public <T> T getObj(String key, Class<T> type) {
        byte[] data = HostCache.get(key);
        if (data == null || data.length == 0) {
            return null;
        }
        switch (serial) {
            case kryo:
                return KryoInnerTool.readFromByteArray(data);
            case protostuff:
                return ProtostuffInnerTool.deserialize(data, type);
            default:
                return null;
        }
    }
}
