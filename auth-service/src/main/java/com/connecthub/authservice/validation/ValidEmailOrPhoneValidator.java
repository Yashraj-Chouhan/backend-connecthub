package com.connecthub.authservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidEmailOrPhoneValidator implements ConstraintValidator<ValidEmailOrPhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || ValidationSupport.isValidEmailOrPhone(value);
    }
}
