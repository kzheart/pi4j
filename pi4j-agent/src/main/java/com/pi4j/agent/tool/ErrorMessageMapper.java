package com.pi4j.agent.tool;

public interface ErrorMessageMapper {
    String map(Throwable error, ToolExecutionContext context);
}
