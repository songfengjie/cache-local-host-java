package com.ext;

public enum HostCacheSerialEnum {
    /**
     * kryo
     */
    kryo,

    /**
     * 性能比kryo要高 不支持 Arrays.subList， guava的ImmutableXxx集合 支持 asList Collections.
     * EMPTY_LIST EMPTY_MAP Collections.singletonList Collections.singleton Collections.singletonMap
     */
    protostuff
}
