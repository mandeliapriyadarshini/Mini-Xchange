package com.minix.data;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * FILE HANDLING (writing): exports a user's trade history to a .csv file that opens in Excel.
 */
public class CsvExporter {

    private final Path exportFolder;

    public CsvExporter(Path exportFolder) {
        this.exportFolder = exportFolder;
    }

    /**
     * Writes the trades to data/exports/trades_<username>_<timestamp>.csv
     * @return the file that was written
     * @throws IOException if the folder or file cannot be written
     */
    public Path export(String username, List<TradeRecord> trades) throws IOException {
        Files.createDirectories(exportFolder);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Path file = exportFolder.resolve("trades_" + username + "_" + stamp + ".csv");
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // try-with-resources: the writer is closed automatically, even if an error happens
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file.toFile()))) {
            out.write("Time,Symbol,Side,Quantity,Price,Value,Counterparty");
            out.newLine();
            for (TradeRecord t : trades) {
                out.write(String.join(",",
                        timeFormat.format(new Date(t.time())),
                        t.symbol(),
                        t.side(),
                        String.valueOf(t.quantity()),
                        String.format("%.2f", t.price()),
                        String.format("%.2f", t.value()),
                        t.counterparty()));
                out.newLine();
            }
        }
        return file;
    }
}
