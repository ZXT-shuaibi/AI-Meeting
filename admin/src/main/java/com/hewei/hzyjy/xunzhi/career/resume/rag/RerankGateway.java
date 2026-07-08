package com.hewei.hzyjy.xunzhi.career.resume.rag;

import java.util.List;

public interface RerankGateway {

    List<String> rerank(String query, List<String> candidates, int limit);
}
