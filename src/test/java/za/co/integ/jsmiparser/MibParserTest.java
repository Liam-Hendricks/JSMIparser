package za.co.integ.jsmiparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MibParserTest {

    // Single source of truth for the example MIB shipped in examples/sample.mib
    // and examples/sample.json (referenced from the README) - loading it here
    // instead of duplicating the text means those two files can't silently
    // drift out of sync with what the parser actually produces.
    private static final Path EXAMPLES_DIR = Path.of("examples");

    private static String sampleMib() throws Exception {
        return Files.readString(EXAMPLES_DIR.resolve("sample.mib"));
    }

    @Test
    void parsesScalarTableAndNotification() throws Exception {
        String json = MibParser.toJson(sampleMib());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        JsonNode moduleIdentity = root.get("testMIB");
        assertEquals("moduleidentity", moduleIdentity.get("class").asText());
        assertEquals("1.3.6.1.4.1.99999", moduleIdentity.get("oid").asText());
        assertEquals("2026-01-01 00:00", moduleIdentity.get("revisions").get(0).asText());
        assertEquals("A small test MIB.", moduleIdentity.get("description").asText());

        JsonNode scalar = root.get("testScalar");
        assertEquals("objecttype", scalar.get("class").asText());
        assertEquals("scalar", scalar.get("nodetype").asText());
        assertEquals("1.3.6.1.4.1.99999.1", scalar.get("oid").asText());
        assertEquals("Integer32", scalar.get("syntax").get("type").asText());
        assertEquals("read-only", scalar.get("maxaccess").asText());

        assertEquals("table", root.get("testTable").get("nodetype").asText());
        assertEquals("row", root.get("testEntry").get("nodetype").asText());
        assertEquals("column", root.get("testIndex").get("nodetype").asText());
        assertEquals("column", root.get("testValue").get("nodetype").asText());
        assertEquals(255, root.get("testValue").get("syntax").get("constraints").get("size").get(0).get("max").asInt());

        JsonNode notification = root.get("testTrap");
        assertEquals("notificationtype", notification.get("class").asText());
        assertEquals(1, notification.get("objects").size());
        assertEquals("testScalar", notification.get("objects").get(0).get("object").asText());
    }

    @Test
    void throwsOnSyntaxError() {
        assertThrows(MibParseException.class, () -> MibParser.toJson("NOT A VALID MIB {{{"));
    }

    @Test
    void multiModuleMapApi() throws Exception {
        var result = MibParser.toJson(java.util.Map.of("TEST-MIB", sampleMib()));
        assertTrue(result.get("TEST-MIB").contains("\"testMIB\""));
    }

    @Test
    void checkedInExampleJsonMatchesParserOutput() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode actual = mapper.readTree(MibParser.toJson(sampleMib()));
        JsonNode expected = mapper.readTree(Files.readString(EXAMPLES_DIR.resolve("sample.json")));
        assertEquals(expected, actual, "examples/sample.json is stale - regenerate it from examples/sample.mib");
    }
}
