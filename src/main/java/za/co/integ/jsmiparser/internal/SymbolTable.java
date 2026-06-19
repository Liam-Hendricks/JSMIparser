package za.co.integ.jsmiparser.internal;

import java.util.HashSet;
import java.util.Set;
import za.co.integ.jsmiparser.internal.gen.SMIParser.AssignmentContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.ModuleBodyContext;
import za.co.integ.jsmiparser.internal.gen.SMIParser.SequenceTypeContext;

/**
 * Pass 1 of the two-pass compilation described in SPEC.md §4: walks a
 * module body once to register every name that carries an OID assignment
 * (so forward references like {@code { mib-2 31 }} resolve regardless of
 * declaration order) and to record which type names are SEQUENCE
 * definitions (needed by {@link JsonGenerator} to classify OBJECT-TYPE
 * "row" nodes).
 */
public final class SymbolTable {

    private final OidResolver oidResolver = new OidResolver();
    private final Set<String> sequenceTypeNames = new HashSet<>();

    public OidResolver oidResolver() {
        return oidResolver;
    }

    public Set<String> sequenceTypeNames() {
        return sequenceTypeNames;
    }

    public void collect(ModuleBodyContext moduleBody) {
        for (AssignmentContext assignment : moduleBody.assignment()) {
            if (assignment.moduleIdentityClause() != null) {
                var ctx = assignment.moduleIdentityClause();
                oidResolver.declare(ctx.identifier().getText(), ctx.objectIdentifierValue());
            } else if (assignment.objectIdentityClause() != null) {
                var ctx = assignment.objectIdentityClause();
                oidResolver.declare(ctx.identifier().getText(), ctx.objectIdentifierValue());
            } else if (assignment.objectTypeClause() != null) {
                var ctx = assignment.objectTypeClause();
                oidResolver.declare(ctx.identifier().getText(), ctx.objectIdentifierValue());
            } else if (assignment.notificationTypeClause() != null) {
                var ctx = assignment.notificationTypeClause();
                oidResolver.declare(ctx.identifier().getText(), ctx.objectIdentifierValue());
            } else if (assignment.objectGroupClause() != null) {
                var ctx = assignment.objectGroupClause();
                oidResolver.declare(ctx.identifier().getText(), ctx.objectIdentifierValue());
            } else if (assignment.notificationGroupClause() != null) {
                var ctx = assignment.notificationGroupClause();
                oidResolver.declare(ctx.identifier().getText(), ctx.objectIdentifierValue());
            } else if (assignment.moduleComplianceClause() != null) {
                var ctx = assignment.moduleComplianceClause();
                oidResolver.declare(ctx.identifier().getText(), ctx.objectIdentifierValue());
            } else if (assignment.agentCapabilitiesClause() != null) {
                var ctx = assignment.agentCapabilitiesClause();
                oidResolver.declare(ctx.identifier().getText(), ctx.objectIdentifierValue());
            } else if (assignment.valueAssignment() != null) {
                var ctx = assignment.valueAssignment();
                if (ctx.objectIdentifierType() != null) {
                    oidResolver.declare(ctx.identifier().getText(), ctx.objectIdentifierValue());
                }
            } else if (assignment.typeAssignment() != null) {
                var ctx = assignment.typeAssignment();
                if (ctx.syntax() instanceof SequenceTypeContext) {
                    sequenceTypeNames.add(ctx.identifier().getText());
                }
            }
            // trapTypeClause assigns a bare Number, not an OID chain - no symbol to declare.
        }
    }
}
