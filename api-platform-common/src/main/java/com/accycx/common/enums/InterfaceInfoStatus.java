package com.accycx.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 接口信息状态枚举类
 */
@Getter
public enum InterfaceInfoStatus {

    OFFLINE(0,"关闭"),
    ONLINE(1,"上线");

    private final int value;
    private final String description;

    InterfaceInfoStatus(int value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 获取值列表
     */
    public static List<Integer> getValues() {
//        流式API：将枚举值转换为流，映射为它们的整数值，并收集到一个列表中返回
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

}
