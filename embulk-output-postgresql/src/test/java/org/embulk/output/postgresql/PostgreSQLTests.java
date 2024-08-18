package org.embulk.output.postgresql;

import org.embulk.test.EmbulkTests;
import org.embulk.test.TestingEmbulk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.embulk.config.ConfigSource;

import static java.util.Locale.ENGLISH;
import static java.util.stream.Collectors.joining;
import static org.embulk.test.EmbulkTests.readSortedFile;

public class PostgreSQLTests
{
    public static ConfigSource baseConfig()
    {
        return EmbulkTests.config("EMBULK_OUTPUT_TEST_CONFIG");
    }

    public static void execute(String sql)
    {
        System.out.println(sql);
        ConfigSource config = baseConfig();
        ProcessBuilder pb = new ProcessBuilder(
                "psql", "-w",
                "--set", "ON_ERROR_STOP=1",
                "--host", config.get(String.class, "host"),
                "--username", config.get(String.class, "user"),
                "--dbname", config.get(String.class, "database"),
                "-c", sql);
        pb.environment().put("PGPASSWORD", config.get(String.class, "password"));
        pb.redirectErrorStream(true);
        int code;
        try {
            Process process = pb.start();
            InputStream inputStream = process.getInputStream();
            byte[] buffer = new byte[8192];
            int readSize;
            while ((readSize = inputStream.read(buffer)) != -1) {
                System.out.write(buffer, 0, readSize);
            }
            code = process.waitFor();
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        if (code != 0) {
            throw new RuntimeException(String.format(ENGLISH,
                        "Command finished with non-zero exit code. Exit code is %d.", code));
        }
    }

    public static String selectRecords(TestingEmbulk embulk, String tableName) throws IOException
    {
        Path temp = embulk.createTempFile("txt");
        Files.delete(temp);
        execute("\\copy " + tableName + " to '" + temp.toString().replace("\\", "\\\\") + "' delimiter ','");
        return readSortedFile(temp);
    }

    public static String selectRecords(TestingEmbulk embulk, String tableName, List<String> columnList) throws IOException
    {
        Path temp = embulk.createTempFile("txt");
        Files.delete(temp);
        final String cols = columnList.stream().collect(Collectors.joining(","));
        execute(String.format("\\COPY (SELECT %s FROM %s) TO '%s' With CSV DELIMITER ',';", cols,  tableName, temp.toString().replace("\\", "\\\\")));
        return readSortedFile(temp);
    }

}
