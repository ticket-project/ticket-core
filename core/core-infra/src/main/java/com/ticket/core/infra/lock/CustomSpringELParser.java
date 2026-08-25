package com.ticket.core.infra.lock;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class CustomSpringELParser {

    public static List<String> getDynamicValue(String prefix, String[] parameterNames, Object[] args, String[] dynamicKey) {
        final List<String> keys = Arrays.stream(dynamicKey)
                .map(key -> parse(key, parameterNames, args))
                .flatMap(CustomSpringELParser::flatten)
                .map(v -> prefix + v)
                .toList();
        if (keys.isEmpty()) {
            throw new IllegalStateException("keys가 0개입니다.");
        }
        return keys;
    }

    private static Object parse(final String key, final String[] parameterNames, final Object[] args) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        return parser.parseExpression(key).getValue(context, Object.class);
    }

    private static Stream<String> flatten(Object value) {
        if (value instanceof Collection<?> c) {
            return c.stream().map(Object::toString);
        }
        return Stream.of(value.toString());
    }
}
