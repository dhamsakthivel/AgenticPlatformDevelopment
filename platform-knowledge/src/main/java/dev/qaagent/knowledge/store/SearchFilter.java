package dev.qaagent.knowledge.store;

import java.util.ArrayList;
import java.util.List;

public record SearchFilter(List<String> sources, String app, String module) {

    public SearchFilter {
        sources = sources == null ? List.of() : List.copyOf(sources.stream().filter(value -> value != null && !value.isBlank()).toList());
    }

    public Query where(String predicate) {
        StringBuilder clause = new StringBuilder(predicate);
        List<Object> args = new ArrayList<>();
        if (!sources.isEmpty()) {
            clause.append(" AND d.source IN (");
            for (int index = 0; index < sources.size(); index++) {
                if (index > 0) {
                    clause.append(", ");
                }
                clause.append('?');
                args.add(sources.get(index));
            }
            clause.append(')');
        }
        if (app != null && !app.isBlank()) {
            clause.append(" AND d.app = ?");
            args.add(app);
        }
        if (module != null && !module.isBlank()) {
            clause.append(" AND d.module = ?");
            args.add(module);
        }
        return new Query(clause.toString(), List.copyOf(args));
    }

    public record Query(String clause, List<Object> args) {
    }
}
