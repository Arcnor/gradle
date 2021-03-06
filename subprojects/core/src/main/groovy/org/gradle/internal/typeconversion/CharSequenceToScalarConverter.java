/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.typeconversion;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gradle.internal.exceptions.DiagnosticsVisitor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class CharSequenceToScalarConverter implements TypeConverters {

    private static final String CANDIDATE = "CharSequence instances.";
    private static final Collection<String> CANDIDATES = Collections.singleton(CANDIDATE);
    private static final Map<Class<?>, NotationParser<Object, ?>> PARSERS = Maps.newHashMap();
    private static final Map<Class<?>, Class<?>> UNBOXED_TYPES = ImmutableMap.<Class<?>, Class<?>>builder()
        .put(Byte.class, byte.class)
        .put(Short.class, short.class)
        .put(Integer.class, int.class)
        .put(Boolean.class, boolean.class)
        .put(Float.class, float.class)
        .put(Character.class, char.class)
        .put(Double.class, double.class)
        .put(Long.class, long.class)
        .build();

    private static void registerConverter(CharSequenceConverter converter, Class<?>... types) {
        for (Class<?> type : types) {
            PARSERS.put(type, NotationParserBuilder
                .toType(type)
                .noImplicitConverters()
                .converter(converter)
                .toComposite());
        }
    }

    private static void convertToCharacter(Object notation, NotationConvertResult<? super Character> result, Class<?> type) throws TypeConversionException {

        String trimmed = notation.toString().trim();
        if (trimmed.length() != 1) {
            throw new TypeConversionException(String.format("Cannot coerce string value '%s' with length %d to type %s",
                trimmed, trimmed.length(), type.getSimpleName()));
        }

        result.converted(trimmed.charAt(0));
    }

    static {

        registerConverter(new NumberConverter<Double>(Double.class) {
            public void doConvert(Object s, NotationConvertResult<? super Double> result) throws TypeConversionException {
                result.converted(Double.valueOf(s.toString()));
            }
        }, Double.class);
        registerConverter(new NumberConverter<Double>(double.class) {
            public void doConvert(Object s, NotationConvertResult<? super Double> result) throws TypeConversionException {
                result.converted(Double.valueOf(s.toString()));
            }
        }, double.class);

        registerConverter(new NumberConverter<Float>(Float.class) {
            public void doConvert(Object s, NotationConvertResult<? super Float> result) throws TypeConversionException {
                result.converted(Float.valueOf(s.toString()));
            }
        }, Float.class);
        registerConverter(new NumberConverter<Float>(float.class) {
            public void doConvert(Object s, NotationConvertResult<? super Float> result) throws TypeConversionException {
                result.converted(Float.valueOf(s.toString()));
            }
        }, float.class);

        registerConverter(new NumberConverter<Integer>(Integer.class) {
            public void doConvert(Object s, NotationConvertResult<? super Integer> result) throws TypeConversionException {
                result.converted(Integer.valueOf(s.toString()));
            }
        }, Integer.class);
        registerConverter(new NumberConverter<Integer>(int.class) {
            public void doConvert(Object s, NotationConvertResult<? super Integer> result) throws TypeConversionException {
                result.converted(Integer.valueOf(s.toString()));
            }
        }, int.class);

        registerConverter(new NumberConverter<Long>(Long.class) {
            public void doConvert(Object s, NotationConvertResult<? super Long> result) throws TypeConversionException {
                result.converted(Long.valueOf(s.toString()));
            }
        }, Long.class);
        registerConverter(new NumberConverter<Long>(long.class) {
            public void doConvert(Object s, NotationConvertResult<? super Long> result) throws TypeConversionException {
                result.converted(Long.valueOf(s.toString()));
            }
        }, long.class);

        registerConverter(new NumberConverter<Short>(Short.class) {
            public void doConvert(Object s, NotationConvertResult<? super Short> result) throws TypeConversionException {
                result.converted(Short.valueOf(s.toString()));
            }
        }, Short.class);
        registerConverter(new NumberConverter<Short>(short.class) {
            public void doConvert(Object s, NotationConvertResult<? super Short> result) throws TypeConversionException {
                result.converted(Short.valueOf(s.toString()));
            }
        }, short.class);

        registerConverter(new NumberConverter<Byte>(Byte.class) {
            public void doConvert(Object s, NotationConvertResult<? super Byte> result) throws TypeConversionException {
                result.converted(Byte.valueOf(s.toString()));
            }
        }, Byte.class);
        registerConverter(new NumberConverter<Byte>(byte.class) {
            public void doConvert(Object s, NotationConvertResult<? super Byte> result) throws TypeConversionException {
                result.converted(Byte.valueOf(s.toString()));
            }
        }, byte.class);

        registerConverter(new NumberConverter<BigDecimal>(BigDecimal.class) {
            public void doConvert(Object s, NotationConvertResult<? super BigDecimal> result) throws TypeConversionException {
                result.converted(new BigDecimal(s.toString()));
            }
        }, BigDecimal.class);

        registerConverter(new NumberConverter<BigInteger>(BigInteger.class) {
            public void doConvert(Object s, NotationConvertResult<? super BigInteger> result) throws TypeConversionException {
                result.converted(new BigInteger(s.toString()));
            }
        }, BigInteger.class);

        CharSequenceConverter<Boolean> booleanConverter = new CharSequenceConverter<Boolean>() {
            public void convert(Object notation, NotationConvertResult<? super Boolean> result) throws TypeConversionException {
                result.converted("true".equals(notation.toString().trim()));
            }
        };
        registerConverter(booleanConverter, Boolean.class, boolean.class);

        registerConverter(new CharSequenceConverter<Character>() {
            public void convert(Object notation, NotationConvertResult<? super Character> result) throws TypeConversionException {
                convertToCharacter(notation, result, Character.class);
            }
        }, Character.class);
        registerConverter(new CharSequenceConverter<Character>() {
            public void convert(Object notation, NotationConvertResult<? super Character> result) throws TypeConversionException {
                convertToCharacter(notation, result, char.class);
            }
        }, char.class);

        registerConverter(new CharSequenceConverter<String>() {
            public void convert(Object notation, NotationConvertResult<? super String> result) throws TypeConversionException {
                result.converted(notation.toString());
            }
        }, String.class);
    }

    public abstract static class CharSequenceConverter<T> implements NotationConverter<Object, T> {
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate(CANDIDATE);
        }
    };

    public abstract static class NumberConverter<T extends Number> extends CharSequenceConverter<T> {
        private final Class<T> type;

        protected NumberConverter(Class<T> type) {
            this.type = type;
        }

        public void convert(Object notation, NotationConvertResult<? super T> result) throws TypeConversionException {
            if (notation instanceof CharSequence) {
                try {
                    doConvert(notation.toString().trim(), result);
                } catch (NumberFormatException e) {
                    throw new TypeConversionException(String.format("Cannot coerce string value '%s' to type %s",
                        notation, type.getSimpleName()));
                }
            }
        }

        protected abstract void doConvert(Object s, NotationConvertResult<? super T> result);
    };

    public static class EnumConverter<T extends Enum> extends CharSequenceConverter<T> {
        private final Class<? extends T> enumType;

        public EnumConverter(Class<? extends T> enumType) {
            this.enumType = enumType;
        }

        public void convert(Object notation, NotationConvertResult<? super T> result) throws TypeConversionException {
            if (notation instanceof CharSequence) {
                result.converted((T)new EnumFromCharSequenceNotationParser(enumType).parseNotation(notation.toString().trim()));
            }
        }
    }

    public Object convert(Object notation, Class type, boolean primitive) throws UnsupportedNotationException, TypeConversionException {

        if (notation == null) {

            if (primitive) {
                throw new UnsupportedNotationException(notation, "Cannot convert null to a primitive type.", null, CANDIDATES);
            }

            return null;
        }

        if (type.isEnum()) {
            return NotationParserBuilder
                .toType(type)
                .noImplicitConverters()
                .converter(new EnumConverter(type))
                .toComposite().parseNotation(notation);
        }

        NotationParser<Object, ?> parser = PARSERS.get(primitive ? UNBOXED_TYPES.get(type) : type);
        if (parser == null) {
            throw new UnsupportedNotationException(notation, "Unsupported type", null, CANDIDATES);
        }

        return parser.parseNotation(notation);
    }
}
