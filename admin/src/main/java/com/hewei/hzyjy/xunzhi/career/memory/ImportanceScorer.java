package com.hewei.hzyjy.xunzhi.career.memory;

import java.util.List;

public interface ImportanceScorer {

    MemoryImportance score(MemoryMessage message, List<MemoryMessage> context);
}
