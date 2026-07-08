package com.hewei.hzyjy.xunzhi.career.ai;

import java.util.List;

public interface EmbeddingGateway {

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
}
