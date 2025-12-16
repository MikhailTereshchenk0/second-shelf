package com.secondshelf.bookservice.mapper;

import com.secondshelf.bookservice.dto.BookResponse;
import com.secondshelf.bookservice.dto.CreateBookRequest;
import com.secondshelf.bookservice.dto.UpdateBookRequest;
import com.secondshelf.bookservice.entity.Book;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface BookMapper {

    BookResponse toResponse(Book book);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Book toEntity(CreateBookRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromRequest(UpdateBookRequest request, @MappingTarget Book book);
}
