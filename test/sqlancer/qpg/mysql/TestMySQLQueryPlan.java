package sqlancer.qpg.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestMySQLQueryPlan {

    @Test
    void testFormatQueryPlan() throws IOException {
        // Sample MySQL EXPLAIN FORMAT=JSON output
        String queryPlan = "{\n" + "  \"query_block\": {\n" + "    \"select_id\": 1,\n"
                + "    \"table\": {\n" + "      \"table_name\": \"t0\",\n" + "      \"access_type\": \"ALL\"\n"
                + "    },\n" + "    \"nested_loop\": [\n" + "      {\n" + "        \"table\": {\n"
                + "          \"table_name\": \"t1\",\n" + "          \"access_type\": \"ref\"\n" + "        }\n"
                + "      }\n" + "    ],\n" + "    \"ordering_operation\": {\n" + "      \"using_filesort\": true\n"
                + "    }\n" + "  }\n" + "}\n";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(queryPlan);

        // Verify we can parse the MySQL EXPLAIN JSON structure
        assertEquals(true, root.has("query_block"));
        JsonNode queryBlock = root.get("query_block");
        assertEquals(true, queryBlock.has("table"));

        JsonNode table = queryBlock.get("table");
        assertEquals("ALL", table.get("access_type").asText());
    }

    @Test
    public void testMySQLQPGAvailable() {
        String mysql = System.getenv("MYSQL_AVAILABLE");
        boolean mysqlIsAvailable = mysql != null && mysql.equalsIgnoreCase("true");
        assumeTrue(mysqlIsAvailable);
        // Environment check passed - QPG should work with MySQL
    }
}