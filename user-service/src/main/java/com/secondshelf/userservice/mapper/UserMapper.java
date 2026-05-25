package com.secondshelf.userservice.mapper;

import com.secondshelf.userservice.dto.CreateUserProfileRequest;
import com.secondshelf.userservice.dto.PrivateUserProfileResponse;
import com.secondshelf.userservice.dto.PublicUserProfileResponse;
import com.secondshelf.userservice.dto.UpdateUserProfileRequest;
import com.secondshelf.userservice.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {

    PublicUserProfileResponse toPublicUserProfileResponse(User user);

    PrivateUserProfileResponse toPrivateUserProfileResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toUser(CreateUserProfileRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateUserFromRequest(UpdateUserProfileRequest request, @MappingTarget User user);
}
