package etalii.adp.fbl.expression;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** A node of a compiled CEL expression. */
sealed interface CelNode {

    default List<CelNode> children() {
        return List.of();
    }

    /** The node's value, which may be null. */
    Object evaluate(CelScope scope, CelProgram.Steps steps);

    record Literal(Object value) implements CelNode {

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            return value;
        }
    }

    record Ident(String name) implements CelNode {

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            CelProgram.step(steps);
            return scope.lookup(name);
        }
    }

    record ListLiteral(List<CelNode> items) implements CelNode {

        @Override
        public List<CelNode> children() {
            return items;
        }

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            List<Object> list = new ArrayList<>(items.size());
            for (CelNode item : items) {
                list.add(item.evaluate(scope, steps));
            }
            return list;
        }
    }

    record MapLiteral(List<Entry> entries) implements CelNode {

        record Entry(CelNode key, CelNode value) {
        }

        @Override
        public List<CelNode> children() {
            List<CelNode> children = new ArrayList<>();
            for (Entry entry : entries) {
                children.add(entry.key());
                children.add(entry.value());
            }
            return children;
        }

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            CelMap map = new CelMap();
            for (Entry entry : entries) {
                String key = CelValues.asString(entry.key().evaluate(scope, steps));
                map.put(key, entry.value().evaluate(scope, steps));
            }
            return map;
        }
    }

    record Member(CelNode target, String name) implements CelNode {

        @Override
        public List<CelNode> children() {
            return List.of(target);
        }

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            CelProgram.step(steps);
            Object value = target.evaluate(scope, steps);
            if (value instanceof CelError) {
                return value;
            }
            if (value instanceof Map<?, ?> map) {
                if (map.containsKey(name)) {
                    return map.get(name);
                }
                throw new CelException("No such key: '" + name + "'.");
            }
            throw new CelException("'" + name + "' is selected from a value that is not a map.");
        }
    }

    record Index(CelNode target, CelNode key) implements CelNode {

        @Override
        public List<CelNode> children() {
            return List.of(target, key);
        }

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            CelProgram.step(steps);
            Object value = target.evaluate(scope, steps);
            Object index = key.evaluate(scope, steps);
            if (value instanceof List<?> list) {
                long i = CelValues.asInt(index);
                if (i < 0 || i >= list.size()) {
                    throw new CelException("Index " + i + " is out of range.");
                }
                return list.get((int) i);
            }
            if (value instanceof Map<?, ?> map) {
                String k = CelValues.asString(index);
                if (map.containsKey(k)) {
                    return map.get(k);
                }
                throw new CelException("No such key: '" + k + "'.");
            }
            throw new CelException("Only a list or a map can be indexed.");
        }
    }

    record Has(CelNode target, String name) implements CelNode {

        @Override
        public List<CelNode> children() {
            return List.of(target);
        }

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            CelProgram.step(steps);
            Object value = target.evaluate(scope, steps);
            if (value instanceof Map<?, ?> map) {
                return map.containsKey(name);
            }
            throw new CelException("has() needs a map.");
        }
    }

    record Unary(String operator, CelNode operand) implements CelNode {

        @Override
        public List<CelNode> children() {
            return List.of(operand);
        }

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            CelProgram.step(steps);
            Object value = operand.evaluate(scope, steps);
            switch (operator) {
                case "!":
                    if (value instanceof Boolean b) {
                        return !b;
                    }
                    throw new CelException("'!' needs a bool.");
                case "-":
                    if (value instanceof Long l) {
                        return -l;
                    }
                    if (value instanceof Double d) {
                        return -d;
                    }
                    throw new CelException("'-' needs a number.");
                default:
                    throw new CelException("Unknown operator '" + operator + "'.");
            }
        }
    }

    record Binary(String operator, CelNode left, CelNode right) implements CelNode {

        @Override
        public List<CelNode> children() {
            return List.of(left, right);
        }

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            CelProgram.step(steps);
            if (operator.equals("&&") || operator.equals("||")) {
                return logical(scope, steps);
            }
            Object l = left.evaluate(scope, steps);
            Object r = right.evaluate(scope, steps);
            switch (operator) {
                case "==":
                    return CelValues.equal(l, r);
                case "!=":
                    return !CelValues.equal(l, r);
                case "<":
                    return CelValues.compare(l, r) < 0;
                case "<=":
                    return CelValues.compare(l, r) <= 0;
                case ">":
                    return CelValues.compare(l, r) > 0;
                case ">=":
                    return CelValues.compare(l, r) >= 0;
                case "in":
                    if (r instanceof List<?> list) {
                        for (Object item : list) {
                            if (CelValues.equal(item, l)) {
                                return true;
                            }
                        }
                        return false;
                    }
                    if (r instanceof Map<?, ?> map) {
                        return l instanceof String key && map.containsKey(key);
                    }
                    throw new CelException("'in' needs a list or a map on its right.");
                case "+":
                    if (l instanceof Long a && r instanceof Long b) {
                        return a + b;
                    }
                    if (l instanceof String a && r instanceof String b) {
                        return a + b;
                    }
                    if (l instanceof List<?> a && r instanceof List<?> b) {
                        List<Object> joined = new ArrayList<>(a.size() + b.size());
                        joined.addAll(a);
                        joined.addAll(b);
                        return joined;
                    }
                    return CelValues.asDouble(l) + CelValues.asDouble(r);
                case "-":
                    if (l instanceof Long a && r instanceof Long b) {
                        return a - b;
                    }
                    return CelValues.asDouble(l) - CelValues.asDouble(r);
                case "*":
                    if (l instanceof Long a && r instanceof Long b) {
                        return a * b;
                    }
                    return CelValues.asDouble(l) * CelValues.asDouble(r);
                case "/":
                    if (l instanceof Long a && r instanceof Long b) {
                        if (b == 0) {
                            throw new CelException("Division by zero.");
                        }
                        return CelValues.divide(a, b);
                    }
                    return CelValues.asDouble(l) / CelValues.asDouble(r);
                case "%":
                    if (l instanceof Long a && r instanceof Long b) {
                        if (b == 0) {
                            throw new CelException("Modulus by zero.");
                        }
                        return CelValues.remainder(a, b);
                    }
                    throw new CelException("'%' needs ints.");
                default:
                    throw new CelException("Unknown operator '" + operator + "'.");
            }
        }

        /** {@code &&} and {@code ||}: a side that decides the result hides the failure of the other. */
        private Object logical(CelScope scope, CelProgram.Steps steps) {
            boolean and = operator.equals("&&");
            Object l;
            try {
                l = left.evaluate(scope, steps);
            } catch (CelException e) {
                l = new CelError(e.getMessage());
            }
            if (and && Boolean.FALSE.equals(l)) {
                return false;
            }
            if (!and && Boolean.TRUE.equals(l)) {
                return true;
            }
            Object r;
            try {
                r = right.evaluate(scope, steps);
            } catch (CelException e) {
                r = new CelError(e.getMessage());
            }
            if (and && Boolean.FALSE.equals(r)) {
                return false;
            }
            if (!and && Boolean.TRUE.equals(r)) {
                return true;
            }
            if (l instanceof Boolean && r instanceof Boolean) {
                return and;
            }
            throw new CelException(l instanceof CelError le ? le.message() : r instanceof CelError re ? re.message() : "'" + operator + "' needs bools.");
        }
    }

    record Conditional(CelNode condition, CelNode then, CelNode otherwise) implements CelNode {

        @Override
        public List<CelNode> children() {
            return List.of(condition, then, otherwise);
        }

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            CelProgram.step(steps);
            Object value = condition.evaluate(scope, steps);
            if (Boolean.TRUE.equals(value)) {
                return then.evaluate(scope, steps);
            }
            if (Boolean.FALSE.equals(value)) {
                return otherwise.evaluate(scope, steps);
            }
            throw new CelException("A conditional needs a bool.");
        }
    }

    record Macro(String name, CelNode target, String variable, CelNode body) implements CelNode {

        @Override
        public List<CelNode> children() {
            return List.of(target, body);
        }

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            CelProgram.step(steps);
            Object value = target.evaluate(scope, steps);
            Collection<?> items;
            if (value instanceof List<?> list) {
                items = list;
            } else if (value instanceof Map<?, ?> map) {
                items = map.keySet();
            } else {
                throw new CelException("'" + name + "' needs a list or a map.");
            }
            List<Object> results = new ArrayList<>();
            for (Object item : items) {
                Object result = body.evaluate(scope.with(variable, item), steps);
                switch (name) {
                    case "all":
                        if (Boolean.FALSE.equals(result)) {
                            return false;
                        }
                        break;
                    case "exists":
                        if (Boolean.TRUE.equals(result)) {
                            return true;
                        }
                        break;
                    case "exists_one":
                    case "filter":
                        if (Boolean.TRUE.equals(result)) {
                            results.add(item);
                        }
                        break;
                    case "map":
                        results.add(result);
                        break;
                    default:
                        break;
                }
            }
            return switch (name) {
                case "all" -> true;
                case "exists" -> false;
                case "exists_one" -> results.size() == 1;
                default -> results;
            };
        }
    }

    /** A function, called on a receiver or, when {@code receiver} is null, on its first argument. */
    record Call(String function, CelNode receiver, List<CelNode> arguments) implements CelNode {

        private static final Duration MATCH_TIMEOUT = Duration.ofMillis(250);

        @Override
        public List<CelNode> children() {
            if (receiver == null) {
                return arguments;
            }
            List<CelNode> children = new ArrayList<>(arguments.size() + 1);
            children.add(receiver);
            children.addAll(arguments);
            return children;
        }

        @Override
        public Object evaluate(CelScope scope, CelProgram.Steps steps) {
            CelProgram.step(steps);
            Object on = receiver == null ? null : receiver.evaluate(scope, steps);
            List<Object> args = new ArrayList<>();
            for (CelNode a : arguments) {
                args.add(a.evaluate(scope, steps));
            }
            if (receiver == null && !args.isEmpty()) {
                on = args.remove(0);
            }
            switch (function) {
                case "size":
                    if (on instanceof String s) {
                        return CelValues.textElements(s);
                    }
                    if (on instanceof List<?> l) {
                        return (long) l.size();
                    }
                    if (on instanceof Map<?, ?> m) {
                        return (long) m.size();
                    }
                    throw new CelException("size() needs a string, a list or a map.");
                case "matches": {
                    String input = CelValues.asString(on);
                    return new BoundedRegex(CelValues.asString(args.get(0)), false, MATCH_TIMEOUT).isMatch(input);
                }
                case "startsWith":
                    return CelValues.asString(on).startsWith(CelValues.asString(args.get(0)));
                case "endsWith":
                    return CelValues.asString(on).endsWith(CelValues.asString(args.get(0)));
                case "contains":
                    return CelValues.asString(on).contains(CelValues.asString(args.get(0)));
                case "replace": {
                    String text = CelValues.asString(on);
                    String old = CelValues.asString(args.get(0));
                    String with = CelValues.asString(args.get(1));
                    if (old.isEmpty()) {
                        // Nothing can be found to replace; here an empty text would match between every two characters.
                        throw new IllegalArgumentException("String cannot be of zero length. (Parameter 'oldValue')");
                    }
                    return text.replace(old, with);
                }
                case "lowerAscii":
                    return CelValues.lower(CelValues.asString(on));
                case "upperAscii":
                    return CelValues.upper(CelValues.asString(on));
                case "int":
                    if (on instanceof Long l) {
                        return l;
                    }
                    if (on instanceof Double d) {
                        return (long) d.doubleValue();
                    }
                    if (on instanceof String s && CelValues.parseInt(s) instanceof Long parsed) {
                        return parsed;
                    }
                    throw new CelException("int() cannot convert this value.");
                case "double":
                    return CelValues.asDouble(on instanceof String s ? (Object) CelValues.parseDouble(s) : on);
                case "string":
                    return CelValues.format(on);
                default:
                    throw new CelException("The function '" + function + "' is not supported.");
            }
        }
    }
}
