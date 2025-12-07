package com.secondshelf.userservice.exception;

public class UserProfileAlreadyExistsException extends RuntimeException {

    public UserProfileAlreadyExistsException(Long id) {
        super("Profile with id " + id + " already exists");
    }
}
