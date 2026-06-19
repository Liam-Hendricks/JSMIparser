package za.co.integ.jsmiparser;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import za.co.integ.jsmiparser.internal.JsonGenerator;
import za.co.integ.jsmiparser.internal.SymbolTable;
import za.co.integ.jsmiparser.internal.gen.SMILexer;
import za.co.integ.jsmiparser.internal.gen.SMIParser;
import za.co.integ.jsmiparser.internal.gen.SMIParser.ModuleDefinitionContext;

/**
 * Parses ASN.1 MIB text and produces JSON structurally identical to pysmi's
 * {@code JsonCodeGen} output. Pure transformation: no file I/O, no HTTP, no
 * classpath scanning. See SPEC.md for the full design.
 */
public final class MibParser {

    private MibParser() {
    }

    /**
     * Parses a single MIB text (one or more module definitions) and
     * returns a JSON object string keyed by every named object across
     * those modules.
     */
    public static String toJson(String mibText) throws MibParseException {
        ObjectNode merged = parseToNode(mibText);
        return merged.toString();
    }

    /**
     * Parses multiple independently-supplied MIB texts. Each module is
     * compiled with its own symbol table, so OID chains that depend on
     * names defined only in another supplied module will fall back to the
     * lenient relative-OID form described in SPEC.md §8 rather than being
     * resolved across modules.
     */
    public static Map<String, String> toJson(Map<String, String> mibs) throws MibParseException {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mibs.entrySet()) {
            try {
                result.put(entry.getKey(), toJson(entry.getValue()));
            } catch (MibParseException e) {
                throw new MibParseException("error in module \"" + entry.getKey() + "\": " + e.getMessage(), e);
            }
        }
        return result;
    }

    private static ObjectNode parseToNode(String mibText) throws MibParseException {
        List<String> errors = new ArrayList<>();

        SMILexer lexer = new SMILexer(CharStreams.fromString(mibText));
        lexer.removeErrorListeners();
        lexer.addErrorListener(collectingListener(errors));

        SMIParser parser = new SMIParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(collectingListener(errors));

        SMIParser.MibFileContext mibFile = parser.mibFile();
        if (!errors.isEmpty()) {
            throw new MibParseException(String.join("; ", errors));
        }

        ObjectNode merged = JsonNodeFactoryHolder.FACTORY.objectNode();
        for (ModuleDefinitionContext moduleDef : mibFile.moduleDefinition()) {
            String moduleName = moduleDef.moduleName().getText();
            SymbolTable symbolTable = new SymbolTable();
            symbolTable.collect(moduleDef.moduleBody());
            JsonGenerator generator = new JsonGenerator(symbolTable, moduleName);
            merged.setAll(generator.generate(moduleDef.moduleBody()));
        }
        return merged;
    }

    private static BaseErrorListener collectingListener(List<String> errors) {
        return new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String msg, RecognitionException e) {
                errors.add("line " + line + ":" + charPositionInLine + " " + msg);
            }
        };
    }

    private static final class JsonNodeFactoryHolder {
        static final com.fasterxml.jackson.databind.node.JsonNodeFactory FACTORY =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
    }
}
