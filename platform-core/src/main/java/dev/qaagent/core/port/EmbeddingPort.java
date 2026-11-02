package dev.qaagent.core.port;

import java.util.List;

public interface EmbeddingPort {

    List<float[]> embed(List<String> texts);

    String modelName();
}
