package com.larryfarming.data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reads Larry's tab-separated data tables from the plugin jar.
 *
 * <p>A table is a UTF-8 text resource with one row per line and cells split on tabs. Lines
 * starting with {@code #} are comments, and blank lines are skipped. A cell holding only
 * {@code -} is empty: it reads as the empty string, as no constant through
 * {@link Row#optionalConstant}, as no ints through {@link Row#integers} and as false through
 * {@link Row#flag}. It is not a number.
 *
 * <p>Tables load in static initialisers, where a bad row would otherwise surface as a bare
 * {@code ExceptionInInitializerError}. So every failure here, and every failure a caller
 * raises through {@link Row#error}, names the resource and the line. Callers index cells
 * from 0; messages count columns from 1, as a reader of the file does.
 *
 * <p>A table's owner may resolve constants only of enums whose own initialisers do not,
 * directly or through another class, read a table that names the owner. Such a cycle leaves
 * one enum uninitialised while the other reads it, and {@link Row#constant} reports it.
 */
public final class Tables {

    /** The cell text that stands for "nothing here". */
    public static final String EMPTY = "-";

    private Tables() {
    }

    /**
     * Every row of a classpath resource, in file order.
     *
     * @param resource an absolute resource path, such as {@code /com/larryfarming/patches.tsv}
     * @throws IllegalStateException if the resource is missing or unreadable
     */
    public static List<Row> read(String resource) {
        if (!resource.startsWith("/")) {
            throw new IllegalArgumentException(resource + ": not an absolute resource path");
        }
        InputStream in = Tables.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException(resource + ": no such resource");
        }
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            int lineNumber = 0;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                rows.add(new Row(resource, lineNumber, line.split("\t", -1)));
            }
        } catch (IOException e) {
            throw new IllegalStateException(resource + ": unreadable", e);
        }
        return Collections.unmodifiableList(rows);
    }

    /**
     * Every row of a resource whose rows all have exactly {@code width} cells.
     *
     * @throws IllegalStateException naming the first row of another width
     */
    public static List<Row> read(String resource, int width) {
        List<Row> rows = read(resource);
        for (Row row : rows) {
            row.requireWidth(width);
        }
        return rows;
    }

    /**
     * The rows of an enum's attribute table, keyed by the constant name in their first cell.
     *
     * <p>An enum's constructor reads its own row from this by {@code name()}, so the enum
     * keeps its constants in Java and its attributes in data. Hold the map in a nested
     * class: the enum's own static fields are not set while its constructors run. Keyed by
     * name, not by constant, because the constants do not exist yet. Call
     * {@link Named#requireAll} once every constant is built, so a row naming no constant
     * fails too.
     *
     * @throws IllegalStateException naming a row of another width or a repeated name
     */
    public static Named byName(String resource, int width) {
        return new Named(resource, read(resource, width));
    }

    /** An enum's attribute rows, by constant name; see {@link #byName}. */
    public static final class Named {

        private final String resource;
        private final Map<String, Row> rows = new HashMap<>();
        private final Set<String> read = new HashSet<>();

        private Named(String resource, List<Row> table) {
            this.resource = resource;
            for (Row row : table) {
                if (rows.put(row.cell(0), row) != null) {
                    throw row.error("a second row for " + row.cell(0));
                }
            }
        }

        /** @throws IllegalStateException if no row names {@code name} */
        public Row of(String name) {
            Row row = rows.get(name);
            if (row == null) {
                // No line to name: the constant is in Java and the table lacks its row.
                throw new IllegalStateException(resource + ": no row for constant " + name);
            }
            read.add(name);
            return row;
        }

        /** @throws IllegalStateException if {@link #of} never asked for a row, such as a misspelt one */
        public void requireAllRead() {
            for (Row row : rows.values()) {
                if (!read.contains(row.cell(0))) {
                    throw row.error("nothing reads " + row.cell(0));
                }
            }
        }

        /** @throws IllegalStateException if a row names none of {@code constants} */
        public void requireAll(Enum<?>[] constants) {
            if (rows.size() != constants.length) {
                Map<String, Row> extra = new HashMap<>(rows);
                for (Enum<?> constant : constants) {
                    extra.remove(constant.name());
                }
                throw extra.values().iterator().next().error("names no constant");
            }
        }
    }

    /** One line of a table: its cells, and where it came from for error messages. */
    public static final class Row {

        private final String resource;
        private final int line;
        private final String[] cells;

        private Row(String resource, int line, String[] cells) {
            this.resource = resource;
            this.line = line;
            this.cells = cells;
        }

        public int width() {
            return cells.length;
        }

        /** @throws IllegalStateException if the row does not have exactly {@code width} cells */
        public Row requireWidth(int width) {
            if (cells.length != width) {
                throw error("expected " + width + " cells, found " + cells.length);
            }
            return this;
        }

        /** The cell's text, or "" for an {@link #EMPTY} cell. */
        public String text(int column) {
            String cell = cell(column);
            return cell.equals(EMPTY) ? "" : cell;
        }

        /** The cell as an int; an {@link #EMPTY} cell is not a number. */
        public int integer(int column) {
            String cell = cell(column);
            try {
                return Integer.parseInt(cell);
            } catch (NumberFormatException e) {
                throw error(columnName(column) + " is not a number: " + cell);
            }
        }

        /** The cell as ints separated by single spaces; an {@link #EMPTY} cell is no ints. */
        public int[] integers(int column) {
            String cell = text(column);
            if (cell.isEmpty()) {
                return new int[0];
            }
            String[] parts = cell.split(" ");
            int[] values = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    values[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    throw error(columnName(column) + " is not a list of numbers: " + cell);
                }
            }
            return values;
        }

        /** The cell as a constant of {@code type}, by exact name. */
        public <E extends Enum<E>> E constant(Class<E> type, int column) {
            return constant(type, column, cell(column));
        }

        private <E extends Enum<E>> E constant(Class<E> type, int column, String cell) {
            if (type.getEnumConstants() == null) {
                // Enum.valueOf would call this "not an enum type", hiding the cycle.
                throw error(type.getSimpleName() + " is not initialised yet: a class-init cycle");
            }
            try {
                return Enum.valueOf(type, cell);
            } catch (IllegalArgumentException e) {
                throw error(columnName(column) + " names no " + type.getSimpleName() + ": " + cell);
            }
        }

        /** The cell as constants of {@code type} separated by single spaces; {@link #EMPTY} is none. */
        public <E extends Enum<E>> List<E> constants(Class<E> type, int column) {
            String cell = text(column);
            List<E> values = new ArrayList<>();
            for (String name : cell.isEmpty() ? new String[0] : cell.split(" ")) {
                values.add(constant(type, column, name));
            }
            return List.copyOf(values);
        }

        /** The cell as a constant of {@code type}, or null for an {@link #EMPTY} cell. */
        public <E extends Enum<E>> E optionalConstant(Class<E> type, int column) {
            return cell(column).equals(EMPTY) ? null : constant(type, column);
        }

        /**
         * Whether the cell holds {@code word}, for a yes/no column written as the word or
         * {@link #EMPTY}.
         */
        public boolean flag(int column, String word) {
            String cell = cell(column);
            if (cell.equals(word)) {
                return true;
            }
            if (cell.equals(EMPTY)) {
                return false;
            }
            throw error(columnName(column) + " must be " + word + " or " + EMPTY + ": " + cell);
        }

        /** An exception naming this row, for a caller rejecting what it holds. */
        public IllegalStateException error(String message) {
            return new IllegalStateException(resource + ":" + line + ": " + message);
        }

        private String cell(int column) {
            if (column < 0 || column >= cells.length) {
                throw error("no " + columnName(column) + " in " + Arrays.toString(cells));
            }
            return cells[column];
        }

        private static String columnName(int column) {
            return "column " + (column + 1);
        }
    }
}
