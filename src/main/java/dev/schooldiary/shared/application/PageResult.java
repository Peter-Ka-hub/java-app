package dev.schooldiary.shared.application;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Materialized page: no JPA entity or lazy collection is passed to HTTP mapping. */
public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
  public static <S, T> PageResult<T> from(Page<S> source, Function<S, T> mapper) {
    return new PageResult<>(
        source.getContent().stream().map(mapper).toList(),
        source.getNumber(),
        source.getSize(),
        source.getTotalElements(),
        source.getTotalPages());
  }
}
