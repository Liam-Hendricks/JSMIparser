package za.co.integ.jsmiparser.internal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import za.co.integ.jsmiparser.internal.gen.SMIParser.ObjectIdentifierValueContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.OidComponentContext;

/**
 * Resolves SMI object identifier chains (e.g. {@code { mib-2 31 }}) to
 * dotted-decimal strings. Seeded with the standard top-level arcs that
 * pysmi treats as pre-compiled stubs from {@code SNMPv2-SMI} /
 * {@code RFC1155-SMI} (see SPEC.md §11): these are referenced by nearly
 * every real-world MIB but never themselves supplied as source text.
 */
public final class OidResolver {

    private static final Map<String, String> WELL_KNOWN_ROOTS = new HashMap<>();

    static {
        WELL_KNOWN_ROOTS.put("ccitt", "0");
        WELL_KNOWN_ROOTS.put("iso", "1");
        WELL_KNOWN_ROOTS.put("joint-iso-ccitt", "2");
        WELL_KNOWN_ROOTS.put("org", "1.3");
        WELL_KNOWN_ROOTS.put("dod", "1.3.6");
        WELL_KNOWN_ROOTS.put("internet", "1.3.6.1");
        WELL_KNOWN_ROOTS.put("directory", "1.3.6.1.1");
        WELL_KNOWN_ROOTS.put("mgmt", "1.3.6.1.2");
        WELL_KNOWN_ROOTS.put("mib-2", "1.3.6.1.2.1");
        WELL_KNOWN_ROOTS.put("transmission", "1.3.6.1.2.1.10");
        WELL_KNOWN_ROOTS.put("experimental", "1.3.6.1.3");
        WELL_KNOWN_ROOTS.put("private", "1.3.6.1.4");
        WELL_KNOWN_ROOTS.put("enterprises", "1.3.6.1.4.1");
        WELL_KNOWN_ROOTS.put("security", "1.3.6.1.5");
        WELL_KNOWN_ROOTS.put("snmpV2", "1.3.6.1.6");
        WELL_KNOWN_ROOTS.put("snmpDomains", "1.3.6.1.6.1");
        WELL_KNOWN_ROOTS.put("snmpProxys", "1.3.6.1.6.2");
        WELL_KNOWN_ROOTS.put("snmpModules", "1.3.6.1.6.3");
    }

    private final Map<String, String> resolved = new HashMap<>(WELL_KNOWN_ROOTS);
    private final Map<String, ObjectIdentifierValueContext> pending = new HashMap<>();
    private final Set<String> inProgress = new HashSet<>();

    /** Registers a name whose OID value is defined elsewhere in this module, for lazy resolution. */
    public void declare(String name, ObjectIdentifierValueContext valueCtx) {
        pending.putIfAbsent(name, valueCtx);
    }

    /**
     * Resolves a name to its dotted OID string. Falls back to the bare name
     * (pysmi's lenient behaviour, e.g. {@code "enterprises.99"}) when the
     * name is neither a well-known root nor declared in this module.
     */
    public String resolve(String name) {
        String cached = resolved.get(name);
        if (cached != null) {
            return cached;
        }
        ObjectIdentifierValueContext ctx = pending.get(name);
        if (ctx == null) {
            return name;
        }
        if (!inProgress.add(name)) {
            return name;
        }
        try {
            String value = resolveValue(ctx);
            resolved.put(name, value);
            return value;
        } finally {
            inProgress.remove(name);
        }
    }

    /** Resolves a full {@code { ... }} OID value expression to a dotted string. */
    public String resolveValue(ObjectIdentifierValueContext ctx) {
        var components = ctx.oidComponentList().oidComponent();
        StringBuilder result = new StringBuilder();
        int startIdx = 0;

        OidComponentContext first = components.get(0);
        if (first.identifier() != null) {
            result.append(resolve(first.identifier().getText()));
            startIdx = 1;
        } else {
            result.append(first.Number_().getText());
            startIdx = 1;
        }

        for (int i = startIdx; i < components.size(); i++) {
            OidComponentContext c = components.get(i);
            String part = c.Number_() != null ? c.Number_().getText() : resolve(c.identifier().getText());
            result.append('.').append(part);
        }
        return result.toString();
    }
}
