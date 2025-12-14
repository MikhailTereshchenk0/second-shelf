package com.secondshelf.userservice.mapper;

import com.secondshelf.userservice.dto.CreateUserProfileRequest;
import com.secondshelf.userservice.dto.UpdateUserProfileRequest;
import com.secondshelf.userservice.dto.UserProfileResponse;
import com.secondshelf.userservice.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserProfileResponse toUserProfileResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toUser(CreateUserProfileRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromRequest(UpdateUserProfileRequest request, @MappingTarget User user);
}
