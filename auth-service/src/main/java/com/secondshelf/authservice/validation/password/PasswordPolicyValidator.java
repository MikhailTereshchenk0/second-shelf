package com.secondshelf.authservice.validation.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPasswordPolicy, Object> {

    private String passwordField;
    private String usernameField;
    private String emailField;

    @Override
    public void initialize(ValidPasswordPolicy constraintAnnotation) {
        this.passwordField = constraintAnnotation.passwordField();
        this.usernameField = constraintAnnotation.usernameField();
        this.emailField = constraintAnnotation.emailField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        BeanWrapperImpl beanWrapper = new BeanWrapperImpl(value);
        String password = getStringProperty(beanWrapper, passwordField);
        String username = getStringProperty(beanWrapper, usernameField);
        String email = getStringProperty(beanWrapper, emailField);

        var violation = PasswordPolicyRules.validate(password, username, email);
        if (violation.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(violation.get())
                .addPropertyNode(passwordField)
                .addConstraintViolation();
        return false;
    }

    private String getStringProperty(BeanWrapperImpl beanWrapper, String propertyName) {
        Object raw = beanWrapper.getPropertyValue(propertyName);
        return raw instanceof String stringValue ? stringValue : null;
    }
}
