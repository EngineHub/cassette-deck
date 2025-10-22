package org.enginehub.jooqext.codgen;

import org.jooq.codegen.DefaultGeneratorStrategy;
import org.jooq.meta.Definition;

/**
 * Custom generator strategy for EngineHub projects.
 */
public class EngineHubGeneratorStrategy extends DefaultGeneratorStrategy {
    @Override
    public String getJavaClassName(Definition definition, Mode mode) {
        var superName = super.getJavaClassName(definition, mode);
        return switch (mode) {
            case POJO -> superName + "Entry";
            default -> superName;
        };
    }
}
