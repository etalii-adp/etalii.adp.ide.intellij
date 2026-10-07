package etalii.adp.fbl.expression;

import java.util.HashMap;
import java.util.Map;

/** The variables of an evaluation, and the variable a macro binds for its body. */
final class CelScope {

    private final Map<String, Object> variables;
    private final CelScope parent;
    private final Map<String, Object> locals = new HashMap<>();

    CelScope(Map<String, Object> variables, CelScope parent) {
        this.variables = variables;
        this.parent = parent;
    }

    CelScope with(String name, Object value) {
        CelScope scope = new CelScope(variables, this);
        scope.locals.put(name, value);
        return scope;
    }

    Object lookup(String name) {
        for (CelScope s = this; s != null; s = s.parent) {
            if (s.locals.containsKey(name)) {
                return s.locals.get(name);
            }
        }
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        throw new CelException("'" + name + "' has no value.");
    }
}
