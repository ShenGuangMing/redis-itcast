package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class R<T> {
    private Boolean success;
    private String errorMsg;
    private T data;
    private Integer total;

    public static <T> R<T> ok() {
        return new R<>(true, null, null,null);
    }

    public static <T> R<T> ok(T t) {
        return new R<>(true, null, t, null);
    }

    public static <T> R<T> ok(T data, Integer total) {
        return new R<>(true, null, data, total);
    }

    public static <T> R<T> error(String errorMsg) {
        return new R<>(false, errorMsg, null, null);
    }
}
