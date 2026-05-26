package com.foundflow.events;

import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.json.JsonMapper;

public final class DomainEventMessageConverterFactory {

    private static final String TRUSTED_EVENTS_PACKAGE = "com.foundflow.events";

    private DomainEventMessageConverterFactory() {
    }

    public static MessageConverter create(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTrustedPackages(TRUSTED_EVENTS_PACKAGE);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
