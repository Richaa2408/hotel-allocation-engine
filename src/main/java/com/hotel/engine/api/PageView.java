package com.hotel.engine.api;

import org.springframework.data.domain.Page;

import java.util.List;

/** A page of results in a stable JSON shape (Spring's Page object is not meant to be serialised directly). */
public record PageView<T>(List<T> content, long total, int page, int size, int pages) {

    public static <T> PageView<T> of(Page<T> page) {
        return new PageView<>(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize(),
                page.getTotalPages());
    }
}
