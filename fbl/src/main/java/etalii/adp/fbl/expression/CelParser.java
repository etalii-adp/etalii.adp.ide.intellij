package etalii.adp.fbl.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The tokenizer and the recursive-descent parser of the CEL subset. */
final class CelParser {

    private static final Set<String> MACROS = Set.of("all", "exists", "exists_one", "filter", "map");
    private static final Set<String> FUNCTIONS = Set.of("size", "matches", "startsWith", "endsWith", "contains", "replace", "lowerAscii", "upperAscii", "int", "double",
            "string");
    private static final Set<String> RELATIONS = Set.of("==", "!=", "<", "<=", ">", ">=", "in");

    private record Token(String kind, String text) {
    }

    private final String source;
    private final List<Token> tokens;
    private int position;

    CelParser(String source) {
        this.source = source;
        this.tokens = tokenize(source);
    }

    CelNode parseExpression() {
        CelNode condition = parseOr();
        if (peek("?")) {
            position++;
            CelNode then = parseOr();
            expect(":");
            CelNode otherwise = parseExpression();
            return new CelNode.Conditional(condition, then, otherwise);
        }
        return condition;
    }

    void expectEnd() {
        if (position < tokens.size()) {
            throw new CelException("Unexpected '" + tokens.get(position).text() + "' in '" + source + "'.");
        }
    }

    private CelNode parseOr() {
        CelNode left = parseAnd();
        while (peek("||")) {
            position++;
            left = new CelNode.Binary("||", left, parseAnd());
        }
        return left;
    }

    private CelNode parseAnd() {
        CelNode left = parseRelation();
        while (peek("&&")) {
            position++;
            left = new CelNode.Binary("&&", left, parseRelation());
        }
        return left;
    }

    private CelNode parseRelation() {
        CelNode left = parseAddition();
        while (position < tokens.size() && tokens.get(position).kind().equals("op") && RELATIONS.contains(tokens.get(position).text())) {
            String op = tokens.get(position++).text();
            left = new CelNode.Binary(op, left, parseAddition());
        }
        return left;
    }

    private CelNode parseAddition() {
        CelNode left = parseMultiplication();
        while (peek("+") || peek("-")) {
            String op = tokens.get(position++).text();
            left = new CelNode.Binary(op, left, parseMultiplication());
        }
        return left;
    }

    private CelNode parseMultiplication() {
        CelNode left = parseUnary();
        while (peek("*") || peek("/") || peek("%")) {
            String op = tokens.get(position++).text();
            left = new CelNode.Binary(op, left, parseUnary());
        }
        return left;
    }

    private CelNode parseUnary() {
        if (peek("!") || peek("-")) {
            String op = tokens.get(position++).text();
            return new CelNode.Unary(op, parseUnary());
        }
        return parsePostfix(parsePrimary());
    }

    private CelNode parsePostfix(CelNode start) {
        CelNode node = start;
        while (true) {
            if (peek(".")) {
                position++;
                String name = expectIdent();
                if (peek("(")) {
                    position++;
                    if (MACROS.contains(name)) {
                        String variable = expectIdent();
                        expect(",");
                        CelNode body = parseExpression();
                        expect(")");
                        node = new CelNode.Macro(name, node, variable, body);
                        continue;
                    }
                    if (!FUNCTIONS.contains(name)) {
                        throw new CelException("The function '" + name + "' is not supported by this CEL evaluator.");
                    }
                    node = new CelNode.Call(name, node, parseArguments());
                    continue;
                }
                node = new CelNode.Member(node, name);
                continue;
            }
            if (peek("[")) {
                position++;
                CelNode key = parseExpression();
                expect("]");
                node = new CelNode.Index(node, key);
                continue;
            }
            return node;
        }
    }

    private List<CelNode> parseArguments() {
        List<CelNode> args = new ArrayList<>();
        if (!peek(")")) {
            args.add(parseExpression());
            while (peek(",")) {
                position++;
                args.add(parseExpression());
            }
        }
        expect(")");
        return args;
    }

    private CelNode parsePrimary() {
        if (position >= tokens.size()) {
            throw new CelException("'" + source + "' ends too early.");
        }
        Token token = tokens.get(position++);
        String text = token.text();
        switch (token.kind()) {
            case "int":
                return new CelNode.Literal(Long.parseLong(text));
            case "double":
                return new CelNode.Literal(Double.parseDouble(text));
            case "string":
                return new CelNode.Literal(text);
            case "ident":
                switch (text) {
                    case "true":
                        return new CelNode.Literal(true);
                    case "false":
                        return new CelNode.Literal(false);
                    case "null":
                        return new CelNode.Literal(null);
                    case "has":
                        expect("(");
                        CelNode target = parsePostfix(parsePrimary());
                        expect(")");
                        if (target instanceof CelNode.Member m) {
                            return new CelNode.Has(m.target(), m.name());
                        }
                        throw new CelException("has() needs a field selection such as has(entry.end).");
                    default:
                        break;
                }
                if (peek("(")) {
                    position++;
                    if (!FUNCTIONS.contains(text)) {
                        throw new CelException("The function '" + text + "' is not supported by this CEL evaluator.");
                    }
                    return new CelNode.Call(text, null, parseArguments());
                }
                return new CelNode.Ident(text);
            case "op":
                if (text.equals("(")) {
                    CelNode inner = parseExpression();
                    expect(")");
                    return inner;
                }
                if (text.equals("[")) {
                    List<CelNode> items = new ArrayList<>();
                    if (!peek("]")) {
                        items.add(parseExpression());
                        while (peek(",")) {
                            position++;
                            if (peek("]")) {
                                break;
                            }
                            items.add(parseExpression());
                        }
                    }
                    expect("]");
                    return new CelNode.ListLiteral(items);
                }
                if (text.equals("{")) {
                    List<CelNode.MapLiteral.Entry> entries = new ArrayList<>();
                    if (!peek("}")) {
                        do {
                            if (peek(",")) {
                                position++;
                            }
                            CelNode key = parseExpression();
                            expect(":");
                            entries.add(new CelNode.MapLiteral.Entry(key, parseExpression()));
                        } while (peek(","));
                    }
                    expect("}");
                    return new CelNode.MapLiteral(entries);
                }
                break;
            default:
                break;
        }
        throw new CelException("Unexpected '" + text + "' in '" + source + "'.");
    }

    private boolean peek(String text) {
        return position < tokens.size() && tokens.get(position).text().equals(text) && tokens.get(position).kind().equals("op");
    }

    private void expect(String text) {
        if (!peek(text)) {
            throw new CelException("Expected '" + text + "' in '" + source + "'.");
        }
        position++;
    }

    private String expectIdent() {
        if (position >= tokens.size() || !tokens.get(position).kind().equals("ident")) {
            throw new CelException("Expected a name in '" + source + "'.");
        }
        return tokens.get(position++).text();
    }

    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (isWhiteSpace(c)) {
                i++;
                continue;
            }
            if (CelValues.isAsciiDigit(c)) {
                int start = i;
                while (i < source.length() && CelValues.isAsciiDigit(source.charAt(i))) {
                    i++;
                }
                boolean isDouble = false;
                if (i + 1 < source.length() && source.charAt(i) == '.' && CelValues.isAsciiDigit(source.charAt(i + 1))) {
                    isDouble = true;
                    i++;
                    while (i < source.length() && CelValues.isAsciiDigit(source.charAt(i))) {
                        i++;
                    }
                }
                if (i < source.length() && (source.charAt(i) == 'u' || source.charAt(i) == 'U')) {
                    throw new CelException("Unsigned integers are not supported by this CEL evaluator.");
                }
                tokens.add(new Token(isDouble ? "double" : "int", source.substring(start, i)));
                continue;
            }
            if (isAsciiLetter(c) || c == '_') {
                int start = i;
                while (i < source.length() && (isAsciiLetter(source.charAt(i)) || CelValues.isAsciiDigit(source.charAt(i)) || source.charAt(i) == '_')) {
                    i++;
                }
                String name = source.substring(start, i);
                // 'in' is an operator spelled as a name.
                tokens.add(new Token(name.equals("in") ? "op" : "ident", name));
                continue;
            }
            if (c == '\'' || c == '"') {
                StringBuilder builder = new StringBuilder();
                i++;
                while (i < source.length() && source.charAt(i) != c) {
                    if (source.charAt(i) == '\\' && i + 1 < source.length()) {
                        i++;
                        builder.append(switch (source.charAt(i)) {
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            case 'r' -> '\r';
                            default -> source.charAt(i);
                        });
                    } else {
                        builder.append(source.charAt(i));
                    }
                    i++;
                }
                if (i >= source.length()) {
                    throw new CelException("A string is not closed in '" + source + "'.");
                }
                i++;
                tokens.add(new Token("string", builder.toString()));
                continue;
            }
            String two = i + 1 < source.length() ? source.substring(i, i + 2) : "";
            if (two.equals("&&") || two.equals("||") || two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=")) {
                tokens.add(new Token("op", two));
                i += 2;
                continue;
            }
            if ("!-+*/%<>?:.,()[]{}".indexOf(c) >= 0) {
                tokens.add(new Token("op", String.valueOf(c)));
                i++;
                continue;
            }
            throw new CelException("'" + c + "' is not supported by this CEL evaluator, in '" + source + "'.");
        }
        return tokens;
    }

    /** White space as Unicode has it: the ASCII controls that separate, the space separators, and the line and paragraph separators. */
    private static boolean isWhiteSpace(char c) {
        return (c >= '\t' && c <= '\r') || (c >= '\u001C' && c <= '\u001F') || c == '\u0085' || Character.isSpaceChar(c);
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
