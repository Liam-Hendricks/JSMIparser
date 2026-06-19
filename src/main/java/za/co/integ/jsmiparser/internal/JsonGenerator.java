package za.co.integ.jsmiparser.internal;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import za.co.integ.jsmiparser.internal.gen.SMIParser.AssignmentContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.BitsTypeContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.ComplianceGroupOrObjectContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.ComplianceModuleContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.ConstraintContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.ConstraintPartContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.DefValValueContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.EnumeratedTypeContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.IdentifierContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.ModuleBodyContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.NamedNumberContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.ObjectIdentifierListContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.PlainTypeContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.RangeItemContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.SequenceOfTypeContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.SequenceTypeContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.String_Context;
import za.co.integ.jsmiparser.internal.gen.SMIParser.SyntaxContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.TypeReferenceContext;

/**
 * Pass 2 of the two-pass compilation: walks a module body (after
 * {@link SymbolTable#collect} has run) and emits the flat JSON object
 * described in SPEC.md §2. One instance per module.
 */
public final class JsonGenerator {

    private final SymbolTable symbolTable;
    private final String moduleName;
    private final JsonNodeFactory nf = JsonNodeFactory.instance;

    /** OID -> nodetype ("table"/"row"), populated as OBJECT-TYPEs are emitted, used to classify columns. */
    private final Map<String, String> nodeTypeByOid = new HashMap<>();

    public JsonGenerator(SymbolTable symbolTable, String moduleName) {
        this.symbolTable = symbolTable;
        this.moduleName = moduleName;
    }

    public ObjectNode generate(ModuleBodyContext moduleBody) {
        ObjectNode root = nf.objectNode();
        for (AssignmentContext assignment : moduleBody.assignment()) {
            String name = null;
            ObjectNode node = null;

            if (assignment.moduleIdentityClause() != null) {
                var ctx = assignment.moduleIdentityClause();
                name = ctx.identifier().getText();
                node = moduleIdentity(ctx);
            } else if (assignment.objectIdentityClause() != null) {
                var ctx = assignment.objectIdentityClause();
                name = ctx.identifier().getText();
                node = objectIdentity(ctx);
            } else if (assignment.objectTypeClause() != null) {
                var ctx = assignment.objectTypeClause();
                name = ctx.identifier().getText();
                node = objectType(ctx);
            } else if (assignment.notificationTypeClause() != null) {
                var ctx = assignment.notificationTypeClause();
                name = ctx.identifier().getText();
                node = notificationType(ctx);
            } else if (assignment.trapTypeClause() != null) {
                var ctx = assignment.trapTypeClause();
                name = ctx.identifier(0).getText();
                node = trapType(ctx);
            } else if (assignment.textualConventionClause() != null) {
                var ctx = assignment.textualConventionClause();
                name = ctx.identifier().getText();
                node = textualConvention(ctx);
            } else if (assignment.objectGroupClause() != null) {
                var ctx = assignment.objectGroupClause();
                name = ctx.identifier().getText();
                node = objectGroup(ctx);
            } else if (assignment.notificationGroupClause() != null) {
                var ctx = assignment.notificationGroupClause();
                name = ctx.identifier().getText();
                node = notificationGroup(ctx);
            } else if (assignment.moduleComplianceClause() != null) {
                var ctx = assignment.moduleComplianceClause();
                name = ctx.identifier().getText();
                node = moduleCompliance(ctx);
            } else if (assignment.agentCapabilitiesClause() != null) {
                var ctx = assignment.agentCapabilitiesClause();
                name = ctx.identifier().getText();
                node = agentCapabilities(ctx);
            } else if (assignment.macroClause() != null) {
                var ctx = assignment.macroClause();
                name = ctx.macroName().getText();
                node = nf.objectNode().put("name", name).put("class", "macro");
            } else if (assignment.valueAssignment() != null) {
                var ctx = assignment.valueAssignment();
                if (ctx.objectIdentifierType() != null) {
                    name = ctx.identifier().getText();
                    node = nf.objectNode()
                            .put("name", name)
                            .put("oid", symbolTable.oidResolver().resolveValue(ctx.objectIdentifierValue()))
                            .put("class", "objectidentity");
                }
            }
            // typeAssignment (plain type / SEQUENCE defs) produce no top-level JSON entry.

            if (node != null) {
                root.set(name, node);
            }
        }
        return root;
    }

    // --- per-class builders -------------------------------------------------

    private ObjectNode moduleIdentity(za.co.integ.jsmiparser.internal.gen.SMIParser.ModuleIdentityClauseContext ctx) {
        ObjectNode node = nf.objectNode();
        String name = ctx.identifier().getText();
        node.put("name", name);
        node.put("oid", symbolTable.oidResolver().resolveValue(ctx.objectIdentifierValue()));
        node.put("class", "moduleidentity");
        ArrayNode revisions = nf.arrayNode();
        for (var rev : ctx.revisionClause()) {
            revisions.add(formatRevisionDate(text(rev.string_(0))));
        }
        node.set("revisions", revisions);
        node.put("lastupdated", text(ctx.string_(0)));
        node.put("organization", text(ctx.string_(1)));
        node.put("contactinfo", text(ctx.string_(2)));
        node.put("description", text(ctx.string_(3)));
        return node;
    }

    private ObjectNode objectIdentity(za.co.integ.jsmiparser.internal.gen.SMIParser.ObjectIdentityClauseContext ctx) {
        ObjectNode node = nf.objectNode();
        node.put("name", ctx.identifier().getText());
        node.put("oid", symbolTable.oidResolver().resolveValue(ctx.objectIdentifierValue()));
        node.put("class", "objectidentity");
        node.put("status", ctx.status_().getText());
        node.put("description", text(ctx.string_()));
        putReference(node, ctx.referencePart());
        return node;
    }

    private ObjectNode objectType(za.co.integ.jsmiparser.internal.gen.SMIParser.ObjectTypeClauseContext ctx) {
        ObjectNode node = nf.objectNode();
        String name = ctx.identifier().getText();
        String oid = symbolTable.oidResolver().resolveValue(ctx.objectIdentifierValue());
        node.put("name", name);
        node.put("oid", oid);
        node.put("class", "objecttype");
        node.set("syntax", syntax(ctx.syntax()));
        if (ctx.unitsPart() != null) {
            node.put("units", text(ctx.unitsPart().string_()));
        }
        node.put("maxaccess", ctx.accessPart().access_().getText());
        node.put("status", ctx.status_().getText());
        if (ctx.descriptionPart() != null) {
            node.put("description", text(ctx.descriptionPart().string_()));
        }
        putReference(node, ctx.referencePart());

        String nodetype = nodeType(ctx, oid);
        if (ctx.indexPart() != null) {
            var idx = ctx.indexPart();
            if (idx.indexTypes() != null) {
                ArrayNode indices = nf.arrayNode();
                for (var it : idx.indexTypes().indexType()) {
                    ObjectNode entry = nf.objectNode();
                    entry.put("object", it.identifier().getText());
                    entry.put("implied", it.IMPLIED() != null);
                    indices.add(entry);
                }
                node.set("indices", indices);
            } else {
                // AUGMENTS clause - pysmi's "augmention" field name (typo preserved for parity).
                node.put("augmention", idx.identifier().getText());
            }
        }
        if (ctx.defValPart() != null) {
            node.set("defval", defVal(ctx.defValPart().defValValue()));
        }
        node.put("nodetype", nodetype);
        return node;
    }

    private String nodeType(za.co.integ.jsmiparser.internal.gen.SMIParser.ObjectTypeClauseContext ctx, String oid) {
        String nodetype;
        if (ctx.syntax() instanceof SequenceOfTypeContext) {
            nodetype = "table";
        } else if (ctx.indexPart() != null) {
            nodetype = "row";
        } else {
            int lastDot = oid.lastIndexOf('.');
            String parentOid = lastDot >= 0 ? oid.substring(0, lastDot) : oid;
            nodetype = "row".equals(nodeTypeByOid.get(parentOid)) ? "column" : "scalar";
        }
        if ("table".equals(nodetype) || "row".equals(nodetype)) {
            nodeTypeByOid.put(oid, nodetype);
        }
        return nodetype;
    }

    private ObjectNode notificationType(za.co.integ.jsmiparser.internal.gen.SMIParser.NotificationTypeClauseContext ctx) {
        ObjectNode node = nf.objectNode();
        node.put("name", ctx.identifier().getText());
        node.put("oid", symbolTable.oidResolver().resolveValue(ctx.objectIdentifierValue()));
        node.put("class", "notificationtype");
        node.set("objects", objectRefs(ctx.objectsPart() == null ? null : ctx.objectsPart().objectIdentifierList()));
        node.put("status", ctx.status_().getText());
        node.put("description", text(ctx.string_()));
        putReference(node, ctx.referencePart());
        return node;
    }

    private ObjectNode trapType(za.co.integ.jsmiparser.internal.gen.SMIParser.TrapTypeClauseContext ctx) {
        ObjectNode node = nf.objectNode();
        node.put("name", ctx.identifier(0).getText());
        String enterpriseOid = symbolTable.oidResolver().resolve(ctx.identifier(1).getText());
        node.put("oid", enterpriseOid + ".0." + ctx.Number_().getText());
        node.put("class", "notificationtype");
        node.set("objects", objectRefs(ctx.variablesPart() == null ? null : ctx.variablesPart().objectIdentifierList()));
        node.put("description", text(ctx.string_()));
        putReference(node, ctx.referencePart());
        return node;
    }

    private ObjectNode textualConvention(za.co.integ.jsmiparser.internal.gen.SMIParser.TextualConventionClauseContext ctx) {
        ObjectNode node = nf.objectNode();
        node.put("name", ctx.identifier().getText());
        node.put("class", "textualconvention");
        if (ctx.displayPart() != null) {
            node.put("displayhint", text(ctx.displayPart().string_()));
        }
        node.put("status", ctx.status_().getText());
        node.put("description", text(ctx.string_()));
        putReference(node, ctx.referencePart());
        node.set("syntax", syntax(ctx.syntax()));
        return node;
    }

    private ObjectNode objectGroup(za.co.integ.jsmiparser.internal.gen.SMIParser.ObjectGroupClauseContext ctx) {
        ObjectNode node = nf.objectNode();
        node.put("name", ctx.identifier().getText());
        node.put("oid", symbolTable.oidResolver().resolveValue(ctx.objectIdentifierValue()));
        node.put("class", "objectgroup");
        node.set("objects", objectRefs(ctx.objectIdentifierList()));
        node.put("status", ctx.status_().getText());
        node.put("description", text(ctx.string_()));
        putReference(node, ctx.referencePart());
        return node;
    }

    private ObjectNode notificationGroup(za.co.integ.jsmiparser.internal.gen.SMIParser.NotificationGroupClauseContext ctx) {
        ObjectNode node = nf.objectNode();
        node.put("name", ctx.identifier().getText());
        node.put("oid", symbolTable.oidResolver().resolveValue(ctx.objectIdentifierValue()));
        node.put("class", "notificationgroup");
        node.set("objects", objectRefs(ctx.objectIdentifierList()));
        node.put("status", ctx.status_().getText());
        node.put("description", text(ctx.string_()));
        putReference(node, ctx.referencePart());
        return node;
    }

    private ObjectNode moduleCompliance(za.co.integ.jsmiparser.internal.gen.SMIParser.ModuleComplianceClauseContext ctx) {
        ObjectNode node = nf.objectNode();
        node.put("name", ctx.identifier().getText());
        node.put("oid", symbolTable.oidResolver().resolveValue(ctx.objectIdentifierValue()));
        node.put("class", "modulecompliance");
        ArrayNode modules = nf.arrayNode();
        for (ComplianceModuleContext cm : ctx.complianceModule()) {
            ObjectNode m = nf.objectNode();
            m.put("module", cm.moduleComplianceName() != null ? cm.moduleComplianceName().identifier().getText() : moduleName);
            ArrayNode mandatory = nf.arrayNode();
            if (cm.mandatoryGroupsPart() != null) {
                for (IdentifierContext id : cm.mandatoryGroupsPart().objectIdentifierList().identifier()) {
                    mandatory.add(id.getText());
                }
            }
            m.set("mandatory", mandatory);
            ArrayNode optional = nf.arrayNode();
            for (ComplianceGroupOrObjectContext go : cm.complianceGroupOrObject()) {
                ObjectNode entry = nf.objectNode();
                if (go.GROUP() != null) {
                    entry.put("group", go.identifier().getText());
                } else {
                    entry.put("object", go.identifier().getText());
                }
                entry.put("description", text(go.string_()));
                optional.add(entry);
            }
            m.set("optional", optional);
            modules.add(m);
        }
        node.set("modulecompliance", modules);
        node.put("status", ctx.status_().getText());
        node.put("description", text(ctx.string_()));
        putReference(node, ctx.referencePart());
        return node;
    }

    private ObjectNode agentCapabilities(za.co.integ.jsmiparser.internal.gen.SMIParser.AgentCapabilitiesClauseContext ctx) {
        ObjectNode node = nf.objectNode();
        node.put("name", ctx.identifier().getText());
        node.put("oid", symbolTable.oidResolver().resolveValue(ctx.objectIdentifierValue()));
        node.put("class", "agentcapabilities");
        node.put("productsrelease", text(ctx.string_(0)));
        node.put("status", ctx.status_().getText());
        node.put("description", text(ctx.string_(1)));
        putReference(node, ctx.referencePart());
        return node;
    }

    // --- shared helpers -------------------------------------------------

    private void putReference(ObjectNode node, za.co.integ.jsmiparser.internal.gen.SMIParser.ReferencePartContext referencePart) {
        if (referencePart != null) {
            node.put("reference", text(referencePart.string_()));
        }
    }

    private ArrayNode objectRefs(ObjectIdentifierListContext list) {
        ArrayNode array = nf.arrayNode();
        if (list == null) {
            return array;
        }
        for (IdentifierContext id : list.identifier()) {
            ObjectNode ref = nf.objectNode();
            ref.put("module", moduleName);
            ref.put("object", id.getText());
            array.add(ref);
        }
        return array;
    }

    private ObjectNode syntax(SyntaxContext ctx) {
        ObjectNode node = nf.objectNode();
        if (ctx instanceof PlainTypeContext plain) {
            node.put("type", typeRefText(plain.typeReference()));
            ConstraintPartContext cp = plain.constraintPart();
            if (cp != null) {
                node.set("constraints", constraints(cp.constraint()));
            }
        } else if (ctx instanceof SequenceOfTypeContext seqOf) {
            node.put("type", typeRefText(seqOf.typeReference()));
        } else if (ctx instanceof BitsTypeContext bits) {
            node.put("type", "Bits");
            ObjectNode constraints = nf.objectNode();
            ObjectNode bitMap = nf.objectNode();
            for (NamedNumberContext nn : bits.namedBitList().namedNumber()) {
                bitMap.put(nn.identifier().getText(), Long.parseLong(nn.Number_().getText()));
            }
            constraints.set("bits", bitMap);
            node.set("constraints", constraints);
        } else if (ctx instanceof EnumeratedTypeContext enumerated) {
            node.put("type", typeRefText(enumerated.typeReference()));
            ObjectNode constraints = nf.objectNode();
            ObjectNode enumMap = nf.objectNode();
            for (NamedNumberContext nn : enumerated.namedNumberList().namedNumber()) {
                enumMap.put(nn.identifier().getText(), Long.parseLong(nn.Number_().getText()));
            }
            constraints.set("enumeration", enumMap);
            node.set("constraints", constraints);
        } else if (ctx instanceof SequenceTypeContext) {
            // Inline SEQUENCE { ... } as an OBJECT-TYPE's own syntax is not valid SMI
            // (rows reference a previously-named SEQUENCE type instead) - stub only.
            node.put("type", "SEQUENCE");
        }
        return node;
    }

    private ObjectNode constraints(ConstraintContext ctx) {
        ObjectNode constraints = nf.objectNode();
        String key = ctx.SIZE() != null ? "size" : "range";
        ArrayNode ranges = nf.arrayNode();
        List<RangeItemContext> items = ctx.rangeList().rangeItem();
        for (RangeItemContext item : items) {
            ObjectNode range = nf.objectNode();
            String min = boundText(item.boundValue(0));
            String max = item.boundValue().size() > 1 ? boundText(item.boundValue(1)) : min;
            putBound(range, "min", min);
            putBound(range, "max", max);
            ranges.add(range);
        }
        constraints.set(key, ranges);
        return constraints;
    }

    private void putBound(ObjectNode node, String field, String value) {
        try {
            node.put(field, Long.parseLong(value));
        } catch (NumberFormatException e) {
            node.put(field, value);
        }
    }

    private String boundText(za.co.integ.jsmiparser.internal.gen.SMIParser.BoundValueContext bv) {
        if (bv.identifier() != null) {
            return symbolTable.oidResolver().resolve(bv.identifier().getText());
        }
        String text = bv.getText();
        return text;
    }

    private com.fasterxml.jackson.databind.JsonNode defVal(DefValValueContext ctx) {
        if (ctx.value() != null) {
            var v = ctx.value();
            if (v.string_() != null) {
                return nf.textNode(text(v.string_()));
            }
            if (v.identifier() != null) {
                return nf.textNode(v.identifier().getText());
            }
            return nf.textNode(v.getText());
        }
        if (ctx.objectIdentifierValue() != null) {
            return nf.textNode(symbolTable.oidResolver().resolveValue(ctx.objectIdentifierValue()));
        }
        if (ctx.bitsValue() != null) {
            ArrayNode bits = nf.arrayNode();
            for (IdentifierContext id : ctx.bitsValue().identifier()) {
                bits.add(id.getText());
            }
            return bits;
        }
        return nf.arrayNode();
    }

    private String formatRevisionDate(String raw) {
        // Raw form is "YYYYMMDDHHMMZ" (optionally with seconds before the Z).
        if (raw.length() < 12) {
            return raw;
        }
        String year = raw.substring(0, 4);
        String month = raw.substring(4, 6);
        String day = raw.substring(6, 8);
        String hour = raw.substring(8, 10);
        String minute = raw.substring(10, 12);
        return year + "-" + month + "-" + day + " " + hour + ":" + minute;
    }

    private String typeRefText(TypeReferenceContext ctx) {
        if (ctx.OCTET() != null) {
            return "OCTET STRING";
        }
        if (ctx.OBJECT() != null) {
            return "OBJECT IDENTIFIER";
        }
        if (ctx.BITS() != null) {
            return "Bits";
        }
        return ctx.identifier().getText();
    }

    private String text(String_Context ctx) {
        String raw = ctx.String_().getText();
        // Strip the surrounding quotes the String_ lexer rule includes, and
        // unescape doubled quotes ("" -> ") per the ASN.1 quoted-string rule.
        String inner = raw.substring(1, raw.length() - 1);
        return inner.replace("\"\"", "\"");
    }
}
