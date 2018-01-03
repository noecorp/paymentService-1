package com.kingfisher.payment.api.optile.service;

import com.kingfisher.payment.api.validator.ValidatorUtil;
import com.kingfisher.payment.api.validator.groups.Optile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;

@Component
public class RestTemplateExtension {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private Validator validator;
    @Autowired
    private ValidatorUtil validatorUtil;

    public <T> ResponseEntity<T> execute(Class<T> responseEntityType, String URL, HttpMethod method, HttpEntity<?> requestEntity) {

        ResponseEntity<T> response = restTemplate.exchange(URL, method, requestEntity, responseEntityType);
        Set<ConstraintViolation<T>> violations = validator.validate(response.getBody(), Optile.class);

        if(!violations.isEmpty()) {
            logger.warn("Not valid Optile Response {}", validatorUtil.collectViolations(violations));
        }

        return response;
    }
}
