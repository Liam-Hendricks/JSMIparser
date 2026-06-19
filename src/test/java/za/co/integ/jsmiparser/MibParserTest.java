package za.co.integ.jsmiparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MibParserTest {

    private static final String SIMPLE_MIB = """
            TEST-MIB DEFINITIONS ::= BEGIN

            IMPORTS
                MODULE-IDENTITY, OBJECT-TYPE, NOTIFICATION-TYPE, enterprises
                    FROM SNMPv2-SMI;

            testMIB MODULE-IDENTITY
                LAST-UPDATED "202601010000Z"
                ORGANIZATION "Integ"
                CONTACT-INFO "support@integ.co.za"
                DESCRIPTION "A small test MIB."
                REVISION "202601010000Z"
                DESCRIPTION "Initial revision."
                ::= { enterprises 99999 }

            testScalar OBJECT-TYPE
                SYNTAX Integer32
                MAX-ACCESS read-only
                STATUS current
                DESCRIPTION "A scalar."
                ::= { testMIB 1 }

            TestEntry ::= SEQUENCE {
                testIndex Integer32,
                testValue OCTET STRING
            }

            testTable OBJECT-TYPE
                SYNTAX SEQUENCE OF TestEntry
                MAX-ACCESS not-accessible
                STATUS current
                DESCRIPTION "A table."
                ::= { testMIB 2 }

            testEntry OBJECT-TYPE
                SYNTAX TestEntry
                MAX-ACCESS not-accessible
                STATUS current
                DESCRIPTION "A row."
                INDEX { testIndex }
                ::= { testTable 1 }

            testIndex OBJECT-TYPE
                SYNTAX Integer32
                MAX-ACCESS not-accessible
                STATUS current
                DESCRIPTION "Row index."
                ::= { testEntry 1 }

            testValue OBJECT-TYPE
                SYNTAX OCTET STRING (SIZE (0..255))
                MAX-ACCESS read-write
                STATUS current
                DESCRIPTION "Row value."
                ::= { testEntry 2 }

            testTrap NOTIFICATION-TYPE
                OBJECTS { testScalar }
                STATUS current
                DESCRIPTION "Something happened."
                ::= { testMIB 3 }

            END
            """;

    @Test
    void parsesScalarTableAndNotification() throws Exception {
        String json = MibParser.toJson(SIMPLE_MIB);
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
        var result = MibParser.toJson(java.util.Map.of("TEST-MIB", SIMPLE_MIB));
        assertTrue(result.get("TEST-MIB").contains("\"testMIB\""));
    }
}
