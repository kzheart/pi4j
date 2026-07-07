package com.pi4j.agent.tool;

import java.util.Objects;

/** {@link ToolExecutionContext} attribute 的类型化键；同名键视为同一个键。 */
public final class AttributeKey<T> {
    private final String name;

    private AttributeKey(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public static <T> AttributeKey<T> of(String name) {
        return new AttributeKey<T>(name);
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttributeKey)) {
            return false;
        }
        return name.equals(((AttributeKey<?>) other).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "AttributeKey(" + name + ")";
    }
}
