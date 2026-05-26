package com.secondshelf.exchangeservice.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class PageableSanitizer {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageableSanitizer() {
    }

    public static Pageable sanitize(Pageable pageable, Set<String> allowedSorts) {
        Pageable source = pageable.isPaged() ? pageable : PageRequest.of(0, DEFAULT_SIZE);
        Sort sort = sanitizeSort(source.getSort(), allowedSorts);
        return PageRequest.of(source.getPageNumber(), Math.min(source.getPageSize(), MAX_SIZE), sort);
    }

    private static Sort sanitizeSort(Sort sort, Set<String> allowedSorts) {
        if (sort.isUnsorted()) {
            return sort;
        }
        for (Sort.Order order : sort) {
            if (!allowedSorts.contains(order.getProperty())) {
                throw new IllegalArgumentException("Unsupported sort field: " + order.getProperty());
            }
        }
        return sort;
    }
}
