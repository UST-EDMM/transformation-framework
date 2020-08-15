package io.github.edmm.plugins.multi.orchestration;

import java.util.List;

public interface GroupExecutor {

    // todo components in context?
    void execute(List<ExecutionCompInfo> components);
}



