package etalii.adp.fbl.document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import etalii.adp.fbl.family.json.JsonMember;
import etalii.adp.fbl.family.json.JsonValue;

/** Maps a binding's JSON onto the typed records, reporting what it cannot map. */
public final class BindingReader {

    private BindingReader() {
    }

    /** The binding, or null when it lacks a part without which nothing else can be read. */
    static FblBinding read(String name, JsonValue json, String pointer, List<Problem> problems) {
        if (!json.isObject()) {
            problems.add(new Problem(pointer, ProblemSeverity.ERROR, "A binding is an object."));
            return null;
        }
        JsonValue claims = json.get("claims");
        JsonValue body = json.get("body");
        JsonValue reader = json.get("reader");
        if (claims == null) {
            problems.add(new Problem(pointer + "/claims", ProblemSeverity.ERROR, "A binding has claims."));
        }
        if (body == null) {
            problems.add(new Problem(pointer + "/body", ProblemSeverity.ERROR, "A binding has a body."));
        }
        if (reader == null) {
            problems.add(new Problem(pointer + "/reader", ProblemSeverity.ERROR, "A binding has a reader."));
        }
        if (problems.stream().anyMatch(p -> p.severity() == ProblemSeverity.ERROR && p.pointer().startsWith(pointer))) {
            return null;
        }

        PluginReader plugin = null;
        if (reader.isObject()) {
            plugin = new PluginReader(Objects.requireNonNullElse(reader.string("plugin"), ""), reader.string("version"), reader.get("args"));
        } else if (!reader.isString() || !reader.text().equals("declared")) {
            problems.add(new Problem(pointer + "/reader", ProblemSeverity.ERROR, "The reader is \"declared\" or a plugin."));
        }

        JsonValue header = json.get("header");
        JsonValue template = json.get("template");
        List<BlockRule> blocks = new ArrayList<>();
        for (JsonValue block : array(json, "blocks")) {
            blocks.add(new BlockRule(orEmpty(block.string("name")), orEmpty(block.string("line")), optionalStrings(block, "within"),
                    block.isTrue("caseInsensitive"), block.string("view")));
        }
        return new FblBinding(
                name,
                localized(json, "title"),
                readClaims(claims),
                readBody(body, pointer + "/body", problems),
                plugin,
                readOnly(json),
                readText(json),
                header == null ? null : new HeaderSettings(header.string("key"), header.get("value"), header.string("line"), header.isTrue("required")),
                json.string("comment"),
                "report".equals(json.string("unmatched")),
                blocks,
                array(json, "elements").stream().map(e -> readRule(e, false)).toList(),
                array(json, "relations").stream().map(e -> readRule(e, true)).toList(),
                readRegistration(json),
                template == null ? null : new TemplateSettings(orEmpty(template.string("text")), stringMap(template.get("byOrigin"), "")));
    }

    private static Claims readClaims(JsonValue json) {
        Marker marker = null;
        JsonValue m = json.get("marker");
        if (m != null) {
            Integer lines = int32(m.get("lines"));
            marker = new Marker(m.string("rootKey"), m.get("value"), m.string("firstLine"), m.string("pattern"), lines != null ? lines : 20);
        }
        Map<String, ReadingClaim> readings = new LinkedHashMap<>();
        JsonValue r = json.get("readings");
        if (r != null && r.isObject()) {
            for (JsonMember p : r.members()) {
                JsonValue suggest = p.value().get("suggest");
                readings.put(p.name(), new ReadingClaim(p.value().isTrue("bare"), suggest != null ? strings(suggest, "contains") : List.of()));
            }
        }
        JsonValue suggest = json.get("suggest");
        return new Claims(
                strings(json, "extensions"),
                strings(json, "names"),
                json.isTrue("shared"),
                json.isTrue("registrationOnly"),
                marker,
                suggest != null ? strings(suggest, "contains") : List.of(),
                strings(json, "origins"),
                readings);
    }

    private static BodySettings readBody(JsonValue json, String pointer, List<Problem> problems) {
        String kind = json.string("kind");
        if (!"file".equals(kind) && !"folder".equals(kind)) {
            problems.add(new Problem(pointer + "/kind", ProblemSeverity.ERROR, "A body's kind is \"file\" or \"folder\"."));
        }
        JsonValue recognise = json.get("recognise");
        boolean recognises = recognise != null && recognise.isObject();
        List<FileRule> files = new ArrayList<>();
        for (JsonValue f : array(json, "files")) {
            files.add(new FileRule(f.string("name"), orEmpty(f.string("glob")), Family.parse(f.string("family")), f.isTrue("readOnly")));
        }
        Integer settle = int32(json.get("settle"));
        return new BodySettings(
                "folder".equals(kind),
                Family.parse(json.string("family")),
                strings(json, "alsoRead").stream().map(Family::parse).filter(Objects::nonNull).toList(),
                recognises ? strings(recognise, "all") : List.of(),
                recognises ? strings(recognise, "any") : List.of(),
                recognises ? strings(recognise, "none") : List.of(),
                files,
                strings(json, "ignore"),
                settle != null ? settle : BodySettings.DEFAULT_SETTLE);
    }

    private static TextDefaults readText(JsonValue json) {
        JsonValue t = json.get("text");
        if (t == null) {
            return TextDefaults.DEFAULT;
        }
        int indent = 2;
        JsonValue i = t.get("indent");
        if (i != null) {
            Integer spaces = int32(i);
            indent = spaces != null ? spaces : 0;
        }
        String newline = t.string("newline");
        JsonValue finalNewline = t.get("finalNewline");
        return new TextDefaults(
                "crlf".equals(newline) ? "\r\n" : "cr".equals(newline) ? "\r" : "\n",
                indent,
                "flush".equals(t.string("sequenceIndent")),
                finalNewline == null || finalNewline.kind() != JsonValue.Kind.FALSE,
                Objects.requireNonNullElse(t.string("quote"), "double"));
    }

    private static Rule readRule(JsonValue json, boolean relation) {
        IdBinding id = null;
        JsonValue idJson = json.get("id");
        if (idJson != null) {
            JsonValue from = idJson.get("from");
            JsonValue sidecar = idJson.get("sidecar");
            id = new IdBinding(from != null ? readSlot(from) : null, sidecar != null ? sidecar.string("key") : null);
        }
        ParentBinding parent = null;
        JsonValue p = json.get("parent");
        if (p != null) {
            parent = new ParentBinding(strings(p, "rules"), p.string("slot"));
        }
        Map<String, AttributeBinding> attributes = new LinkedHashMap<>();
        JsonValue attrs = json.get("attributes");
        if (attrs != null && attrs.isObject()) {
            for (JsonMember a : attrs.members()) {
                attributes.put(a.name(), readAttribute(a.value()));
            }
        }
        InsertSettings insert = null;
        JsonValue ins = json.get("insert");
        if (ins != null) {
            String place;
            String before = null;
            JsonValue pl = ins.get("place");
            if (pl != null && pl.isObject()) {
                place = "before";
                before = pl.string("before");
            } else {
                place = pl != null && pl.isString() ? pl.text() : "end";
            }
            CreateContainer create = null;
            JsonValue c = ins.get("create");
            if (c != null) {
                JsonValue at = c.get("at");
                if (at != null && at.isString()) {
                    create = new CreateContainer(at.text(), null, c.string("text"));
                } else if (at != null && at.isObject() && !at.members().isEmpty()) {
                    JsonMember first = at.members().get(0);
                    create = new CreateContainer(first.name(), first.value().isString() ? first.value().text() : null, c.string("text"));
                }
            }
            insert = new InsertSettings(place, before, ins.string("container"), create, strings(ins, "keys"), ins.string("emit"), ins.string("skeleton"),
                    ins.string("when"));
        }
        RemoveSettings remove = null;
        JsonValue rem = json.get("remove");
        if (rem != null) {
            remove = new RemoveSettings(strings(rem, "cascade"), "remove-when-empty".equals(rem.string("container")));
        }
        JsonValue source = json.get("source");
        JsonValue target = json.get("target");
        return new Rule(
                orEmpty(json.string("name")),
                orEmpty(json.string("type")),
                relation,
                json.string("at"),
                json.string("line"),
                optionalStrings(json, "within"),
                json.isTrue("opens"),
                json.isTrue("caseInsensitive"),
                strings(json, "files"),
                json.string("when"),
                id,
                parent,
                attributes,
                source != null ? readAttribute(source) : null,
                target != null ? readAttribute(target) : null,
                insert,
                remove,
                "snapshot".equals(json.string("undo")),
                readOnly(json));
    }

    static Slot readSlot(JsonValue json) {
        return new Slot(json.string("key"), json.string("attribute"), json.isTrue("text"), json.string("child"), json.string("group"), json.string("parent"),
                json.string("capture"), json.string("value"), json.string("word"), json.isTrue("flag"));
    }

    private static AttributeBinding readAttribute(JsonValue json) {
        Integer decimals = null;
        JsonValue n = json.get("number");
        if (n != null && n.isObject()) {
            decimals = int32(n.get("decimals"));
        }
        Map<String, String> map = null;
        JsonValue m = json.get("map");
        if (m != null && m.isObject()) {
            map = new LinkedHashMap<>();
            for (JsonMember p : m.members()) {
                map.put(p.name(), scalarText(p.value()));
            }
        }
        CreateChild create = null;
        JsonValue c = json.get("create");
        if (c != null) {
            JsonValue pl = c.get("place");
            create = pl != null && pl.isObject()
                    ? new CreateChild(orEmpty(c.string("emit")), "before", pl.string("before"))
                    : new CreateChild(orEmpty(c.string("emit")), pl != null && pl.isString() ? pl.text() : "last", null);
        }
        ReferenceBinding reference = null;
        JsonValue r = json.get("reference");
        if (r != null) {
            reference = new ReferenceBinding(strings(r, "to"), orEmpty(r.string("by")));
        }
        JsonValue override = json.get("override");
        return new AttributeBinding(
                readSlot(json),
                json.string("empty"),
                stringMap(json.get("absent"), "insert"),
                json.get("default"),
                decimals,
                "keep-precision".equals(json.string("time")),
                json.string("style"),
                reference,
                map,
                override != null ? readSlot(override) : null,
                "html-paragraphs".equals(json.string("content")),
                create,
                readOnly(json));
    }

    private static RegistrationSettings readRegistration(JsonValue json) {
        JsonValue r = json.get("registration");
        if (r == null) {
            return RegistrationSettings.DEFAULT;
        }
        JsonValue headers = r.get("headers");
        JsonValue resource = r.get("resource");
        return new RegistrationSettings(
                headers != null && headers.isObject() ? headers.members().stream().map(JsonMember::name).toList() : List.of(),
                r.isTrue("createOnFirstPlacement"),
                resource != null ? resource.string("capture") : null,
                r.string("legacyLayout"),
                r.string("legacyIdentities"));
    }

    /** A scalar as the text a body would hold: a string's value, a literal as written, nothing for null. */
    public static String scalarText(JsonValue value) {
        return switch (value.kind()) {
            case STRING, TRUE, FALSE, NUMBER -> value.text();
            case NULL -> "";
            case OBJECT, ARRAY -> "";
        };
    }

    private static String readOnly(JsonValue json) {
        JsonValue r = json.get("readOnly");
        if (r == null) {
            return null;
        }
        return switch (r.kind()) {
            case TRUE -> "";
            case STRING -> r.text();
            case OBJECT -> localizedValue(r);
            default -> null;
        };
    }

    private static String localized(JsonValue json, String name) {
        JsonValue v = json.get(name);
        return v == null ? null : v.isString() ? v.text() : localizedValue(v);
    }

    /** The English text of a localized value, or the first there is. */
    private static String localizedValue(JsonValue value) {
        String en = value.string("en");
        if (en != null) {
            return en;
        }
        return value.members().stream().map(JsonMember::value).filter(JsonValue::isString).map(JsonValue::text).findFirst().orElse(null);
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }

    /** A number that is written as an integer and fits an int, or null. */
    private static Integer int32(JsonValue value) {
        if (value != null && value.typed() instanceof Long number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
            return number.intValue();
        }
        return null;
    }

    private static List<String> strings(JsonValue json, String name) {
        JsonValue v = json.get(name);
        if (v == null || !v.isArray()) {
            return List.of();
        }
        return v.members().stream().map(JsonMember::value).filter(JsonValue::isString).map(JsonValue::text).toList();
    }

    private static List<String> optionalStrings(JsonValue json, String name) {
        return json.get(name) != null ? strings(json, name) : null;
    }

    private static List<JsonValue> array(JsonValue json, String name) {
        JsonValue v = json.get(name);
        return v != null && v.isArray() ? v.members().stream().map(JsonMember::value).toList() : List.of();
    }

    /** An object's members as text, in document order; a member that is not a string takes the fallback. */
    private static Map<String, String> stringMap(JsonValue json, String fallback) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json != null && json.isObject()) {
            for (JsonMember p : json.members()) {
                map.put(p.name(), p.value().isString() ? p.value().text() : fallback);
            }
        }
        return map;
    }
}
