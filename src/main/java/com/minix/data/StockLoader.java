package com.minix.data;

import com.minix.model.Stock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * FILE HANDLING (reading): loads the list of listed companies from data/stocks.csv.
 *
 * Format, one stock per line:   SYMBOL,Company Name,OpeningPrice
 * Lines starting with # are comments. If the file does not exist, a default one is
 * created, so you can edit it to add your own stocks without touching any Java code.
 */
public final class StockLoader {

    private static final String DEFAULT_FILE = """
            # Mini-Xchange listed companies (all fictional)
            # SYMBOL,Company Name,Opening Price
            DSRT,Desert Solar Ltd,182.50
            PALM,Palm Logistics,96.20
            FALC,Falcon Air,254.00
            BYTE,ByteCraft Technologies,421.75
            """;

    private StockLoader() { }

    public static List<Stock> load(Path file) throws IOException {
        if (!Files.exists(file)) {
            writeDefault(file);
        }
        List<Stock> stocks = new ArrayList<>();
        int lineNo = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            while ((line = in.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // StringTokenizer splits the line into pieces at each comma
                StringTokenizer tokens = new StringTokenizer(line, ",");
                if (tokens.countTokens() != 3) {
                    System.err.println("stocks.csv line " + lineNo + " skipped (expected 3 values): " + line);
                    continue;
                }
                String symbol = tokens.nextToken().trim();
                String name = tokens.nextToken().trim();
                try {
                    double price = Double.parseDouble(tokens.nextToken().trim());
                    stocks.add(new Stock(symbol, name, price));
                } catch (NumberFormatException e) {
                    System.err.println("stocks.csv line " + lineNo + " skipped (bad price): " + line);
                }
            }
        }
        if (stocks.isEmpty()) {
            throw new IOException("No valid stocks found in " + file);
        }
        return stocks;
    }

    private static void writeDefault(Path file) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file.toFile()))) {
            out.write(DEFAULT_FILE);
        }
    }
}
