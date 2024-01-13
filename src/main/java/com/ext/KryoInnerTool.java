package com.ext;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import de.javakaffee.kryoserializers.*;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationHandler;
import java.util.*;

public class KryoInnerTool {
    private static final String DEFAULT_ENCODING = "UTF-8";
    private static ArrayList<Integer> l = new ArrayList();

    static {
        l.add(1);
    }

    /**
     * 每个线程的 Kryo 实例
     */
    private static final ThreadLocal<Kryo> KRYO_LOCAL = new ThreadLocal<Kryo>() {

        @Override
        protected Kryo initialValue() {
            Kryo kryo = new Kryo();

            /**
             * 不要轻易改变这里的配置！更改之后，序列化的格式就会发生变化，
             * 上线的同时就必须清除 Redis 里的所有缓存，
             * 否则那些缓存再回来反序列化的时候，就会报错
             */
            //支持对象循环引用（否则会栈溢出）
            //默认值就是 true，添加此行的目的是为了提醒维护者，不要改变这个配置
            kryo.setReferences(true);

            //不强制要求注册类（注册行为无法保证多个 JVM 内同一个类的注册编号相同；而且业务系统中大量的 Class 也难以一一注册）
            //默认值就是 false，添加此行的目的是为了提醒维护者，不要改变这个配置
            kryo.setRegistrationRequired(false);
            kryo.setDefaultSerializer(CompatibleFieldSerializer.class);
            //Fix the NPE bug when deserializing Collections.
            ((Kryo.DefaultInstantiatorStrategy) kryo.getInstantiatorStrategy())
                    .setFallbackInstantiatorStrategy(new StdInstantiatorStrategy());
            // 注册部分特殊的集合类的序列化方式
            kryo.register(l.subList(0, 1).getClass(), new SubListSerializers.ArrayListSubListSerializer());
            kryo.register(Arrays.asList("").getClass(), new ArraysAsListSerializer());
            kryo.register(Collections.EMPTY_LIST.getClass(), new CollectionsEmptyListSerializer());
            kryo.register(Collections.EMPTY_MAP.getClass(), new CollectionsEmptyMapSerializer());
            kryo.register(Collections.EMPTY_SET.getClass(), new CollectionsEmptySetSerializer());
            kryo.register(Collections.singletonList("").getClass(), new CollectionsSingletonListSerializer());
            kryo.register(Collections.singleton("").getClass(), new CollectionsSingletonSetSerializer());
            kryo.register(Collections.singletonMap("", "").getClass(), new CollectionsSingletonMapSerializer());
            kryo.register(GregorianCalendar.class, new GregorianCalendarSerializer());
            kryo.register(InvocationHandler.class, new JdkProxySerializer());
            SynchronizedCollectionsSerializer.registerSerializers(kryo);
            UnmodifiableCollectionsSerializer.registerSerializers(kryo);

            return kryo;
        }
    };

    /**
     * 获得当前线程的 Kryo 实例
     *
     * @return 当前线程的 Kryo 实例
     */
    public static Kryo getInstance() {
        return KRYO_LOCAL.get();
    }

    //-----------------------------------------------
    //          序列化/反序列化对象，及类型信息
    //          序列化的结果里，包含类型的信息
    //          反序列化时不再需要提供类型
    //-----------------------------------------------

    /**
     * 将对象【及类型】序列化为字节数组
     *
     * @param <T> 对象的类型
     * @param obj 任意对象
     * @return 序列化后的字节数组 byte [ ]
     */
    public static <T> byte[] writeToByteArray(T obj) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Output output = new Output(byteArrayOutputStream);

        Kryo kryo = getInstance();
        kryo.writeClassAndObject(output, obj);
        output.flush();

        return byteArrayOutputStream.toByteArray();
    }

    /**
     * 将对象【及类型】序列化为 String
     * 利用了 Base64 编码
     *
     * @param <T> 对象的类型
     * @param obj 任意对象
     * @return 序列化后的字符串 string
     */
    public static <T> String writeToString(T obj) {
        try {
            return new String(Base64.getEncoder().encode(writeToByteArray(obj)), DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 将字节数组反序列化为原对象
     *
     * @param <T>       原对象的类型
     * @param byteArray writeToByteArray 方法序列化后的字节数组
     * @return 原对象 t
     */
    @SuppressWarnings("unchecked")
    public static <T> T readFromByteArray(byte[] byteArray) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray);
        Input input = new Input(byteArrayInputStream);

        Kryo kryo = getInstance();
        return (T) kryo.readClassAndObject(input);
    }

    /**
     * 将 String 反序列化为原对象
     * 利用了 Base64 编码
     *
     * @param <T> 原对象的类型
     * @param str writeToString 方法序列化后的字符串
     * @return 原对象 t
     */
    public static <T> T readFromString(String str) {
        try {
            return readFromByteArray(Base64.getDecoder().decode(str.getBytes(DEFAULT_ENCODING)));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
