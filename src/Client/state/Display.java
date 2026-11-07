package Client.state;

import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Display {
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static String time(long millis) { return DATE.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())); }
    public static long parseTime(String text) { return LocalDateTime.parse(text.strip(), DATE).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    public static String price(long value) { return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value) + " ₫"; }
    public static String role(String value) {
        return switch (value) { case "SELLER" -> "Người bán"; case "ADMIN" -> "Quản trị"; default -> "Người mua"; };
    }
}
